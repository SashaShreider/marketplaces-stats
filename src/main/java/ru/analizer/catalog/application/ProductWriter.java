package ru.analizer.catalog.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.analizer.account.domain.SellerAccount;
import ru.analizer.catalog.domain.*;
import ru.analizer.catalog.domain.AuthorExtractor.Author;
import ru.analizer.catalog.repository.OzonProductRepository;
import ru.analizer.integration.model.ProductAttributeEntry;
import ru.analizer.integration.model.ProductEntry;
import ru.analizer.integration.ozon.OzonProductAttributes;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Запись товаров каталога — по одному товару в своей транзакции.
 *
 * <p>Отдельный компонент, а не метод {@link CatalogImportService}, по той же причине,
 * что и {@link ru.analizer.sync.application.AccrualWriter}: транзакция должна применяться через
 * прокси Spring, а прямой вызов метода того же класса обходит прокси и
 * {@code @Transactional} молча не срабатывает.
 *
 * <p>Транзакция на товар, а не на страницу: каталог — это сотни товаров, и одна
 * транзакция на всю страницу держала бы себя при обрыве связи целиком.
 *
 * <p>Характеристики и авторы пишутся обычным SQL, а не через JPA. У товара их
 * восемнадцать и единица, и через JPA это двадцать отдельных запросов на товар:
 * загрузка каталога в сотни товаров упиралась не в OZON, а в сотни обращений к базе.
 * Одним запросом на товар вместо двадцати каталог грузится в разы быстрее.
 */
@Component
public class ProductWriter {

    private static final String INSERT_ATTRIBUTE = """
            insert into ozon_product_attribute
                (seller_account_id, sku, attribute_id, position, value, dictionary_value_id)
            values (?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_AUTHOR = """
            insert into product_author
                (seller_account_id, sku, author_raw, source, is_primary, position)
            values (?, ?, ?, ?, ?, ?)
            """;

    private static final String DELETE_ATTRIBUTES = """
            delete from ozon_product_attribute
            where seller_account_id = ? and sku = ?
            """;

    private static final String DELETE_AUTHORS = """
            delete from product_author
            where seller_account_id = ? and sku = ?
            """;

    private final OzonProductRepository productRepository;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ProductWriter(OzonProductRepository productRepository, JdbcTemplate jdbc, Clock clock) {
        this.productRepository = productRepository;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void persist(SellerAccount account, ProductEntry entry) {
        Long accountId = account.getId();
        Instant now = Instant.now(clock);

        Optional<OzonProduct> stored = productRepository
                .findBySellerAccountIdAndSku(accountId, entry.sku());
        OzonProduct product = stored.orElseGet(() -> new OzonProduct(account, entry.sku(), now));

        List<Author> authors = extractAuthors(entry);
        product.refreshFrom(entry.ozonProductId(), entry.offerId(), entry.name(), entry.barcode(),
                entry.typeId(), entry.descriptionCategoryId(), entry.primaryImage(),
                entry.attributeValue(OzonProductAttributes.ISBN),
                entry.weightGrams(), entry.widthMm(), entry.heightMm(), entry.depthMm(),
                entry.modelId(), entry.rawJson(), now, entry.archived());
        productRepository.save(product);
        productRepository.flush();

        // Прежние строки удаляются только у товара, который в базе уже был: у нового
        // товара строк не бывает, а удалять всё подряд — значит ещё и спрашивать базу
        // о том, чего нет.
        if (stored.isPresent()) {
            jdbc.update(DELETE_ATTRIBUTES, accountId, entry.sku());
            jdbc.update(DELETE_AUTHORS, accountId, entry.sku());
        }

        insertAttributes(accountId, entry);
        insertAuthors(accountId, entry, authors);
    }

    private void insertAttributes(Long accountId, ProductEntry entry) {
        List<Object[]> batch = new ArrayList<>();
        for (ProductAttributeEntry attribute : entry.attributes()) {
            batch.add(new Object[]{accountId, entry.sku(), attribute.attributeId(),
                    attribute.position(), attribute.value(), attribute.dictionaryValueId()});
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate(INSERT_ATTRIBUTE, batch);
        }
    }

    private void insertAuthors(Long accountId, ProductEntry entry, List<Author> authors) {
        if (authors.isEmpty()) {
            return;
        }
        List<Object[]> batch = new ArrayList<>(authors.size());
        for (Author author : authors) {
            batch.add(new Object[]{accountId, entry.sku(), author.raw(),
                    author.primary() ? "DECLARED" : "COVER", author.primary(), author.position()});
        }
        jdbc.batchUpdate(INSERT_AUTHOR, batch);
    }

    /**
     * Авторы товара из обоих атрибутов.
     *
     * <p>Приоритет у 4182 «Автор»: он и есть карточка товара. 105 «Автор на обложке»
     * подхватывается, только если в карточке автора нет — иначе отчёт показывал бы одно
     * и то же лицо дважды, из двух разных строк.
     *
     * <p>Берутся <b>все</b> значения атрибута, а не первое: «Автор на обложке» у части
     * товаров приходит массивом, где каждый автор отдельным элементом, и взятие одного
     * значения молча потеряло бы остальных.
     *
     * <p>Товаров без автора в выгрузке 9 из 108 (бумага, календари, папки), и для них
     * просто не создаётся ни одной строки — товар остаётся видимым в отчёте, но без
     * автора.
     */
    private List<Author> extractAuthors(ProductEntry entry) {
        List<String> declared = entry.attributeValues(OzonProductAttributes.AUTHOR);
        List<Author> result = new ArrayList<>(AuthorExtractor.extract(declared, true));
        if (declared.isEmpty()) {
            result.addAll(AuthorExtractor.extract(
                    entry.attributeValues(OzonProductAttributes.COVER_AUTHOR), true));
        }
        return result;
    }
}
