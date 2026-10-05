package ru.analizer.catalog;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.analizer.catalog.AuthorExtractor.Author;
import ru.analizer.marketplace.ProductAttributeEntry;
import ru.analizer.marketplace.ProductEntry;
import ru.analizer.marketplace.ozon.OzonProductAttributes;
import ru.analizer.persistence.entity.OzonProduct;
import ru.analizer.persistence.entity.OzonProductAttribute;
import ru.analizer.persistence.entity.ProductAuthor;
import ru.analizer.persistence.repository.OzonProductAttributeRepository;
import ru.analizer.persistence.repository.OzonProductRepository;
import ru.analizer.persistence.repository.ProductAuthorRepository;
import ru.analizer.persistence.entity.SellerAccount;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Запись товаров каталога — по одному товару в своей транзакции.
 *
 * <p>Отдельный компонент, а не метод {@link CatalogSyncService}, по той же причине,
 * что и {@link ru.analizer.sync.AccrualWriter}: транзакция должна применяться через
 * прокси Spring, а прямой вызов метода того же класса обходит прокси и
 * {@code @Transactional} молча не срабатывает.
 *
 * <p>Транзакция на товар, а не на страницу: каталог — это сотни товаров, и одна
 * транзакция на всю страницу держала бы себя при обрыве связи целиком.
 */
@Component
public class CatalogWriter {

    private final OzonProductRepository productRepository;
    private final OzonProductAttributeRepository attributeRepository;
    private final ProductAuthorRepository authorRepository;
    private final Clock clock;

    public CatalogWriter(OzonProductRepository productRepository,
                         OzonProductAttributeRepository attributeRepository,
                         ProductAuthorRepository authorRepository,
                         Clock clock) {
        this.productRepository = productRepository;
        this.attributeRepository = attributeRepository;
        this.authorRepository = authorRepository;
        this.clock = clock;
    }

    @Transactional
    public void persist(SellerAccount account, ProductEntry entry) {
        Long accountId = account.getId();
        Instant now = Instant.now(clock);

        OzonProduct product = productRepository
                .findBySellerAccountIdAndSku(accountId, entry.sku())
                .orElseGet(() -> new OzonProduct(account, entry.sku(), now));

        List<Author> authors = extractAuthors(entry);
        product.refreshFrom(entry.ozonProductId(), entry.offerId(), entry.name(), entry.barcode(),
                entry.typeId(), entry.descriptionCategoryId(), entry.primaryImage(),
                entry.attributeValue(OzonProductAttributes.ISBN),
                entry.weightGrams(), entry.widthMm(), entry.heightMm(), entry.depthMm(),
                entry.modelId(), entry.rawJson(), now);
        product.applyAuthors(authorKeys(authors), authorSurnames(authors));
        productRepository.save(product);
        productRepository.flush();

        // Удаления и вставки в одном порядке требуют flush между ними, иначе новая
        // строка встанет раньше удалённой и упадёт на уникальном индексе.
        attributeRepository.deleteForProduct(accountId, entry.sku());
        authorRepository.deleteForProduct(accountId, entry.sku());
        attributeRepository.flush();
        authorRepository.flush();

        for (ProductAttributeEntry attribute : entry.attributes()) {
            attributeRepository.save(new OzonProductAttribute(accountId, entry.sku(),
                    attribute.attributeId(), attribute.position(),
                    attribute.value(), attribute.dictionaryValueId()));
        }
        for (Author author : authors) {
            authorRepository.save(new ProductAuthor(accountId, entry.sku(), author.raw(),
                    author.primary() ? "DECLARED" : "COVER", author.primary(), author.position()));
        }
    }

    /**
     * Авторы товара из обоих атрибутов.
     *
     * <p>Приоритет у 4182 «Автор»: он и есть карточка товара. 105 «Автор на обложке»
     * подхватывается, только если в карточке автора нет — иначе отчёт показывал бы одно
     * и то же лицо дважды, из двух разных строк.
     *
     * <p>Товаров без автора в выгрузке 9 из 108 (бумага, календари, папки), и для них
     * просто не создаётся ни одной строки — товар остаётся видимым в отчёте, но без
     * автора.
     */
    private List<Author> extractAuthors(ProductEntry entry) {
        String declared = entry.attributeValue(OzonProductAttributes.AUTHOR);
        List<Author> result = new ArrayList<>(AuthorExtractor.extract(declared, true));
        if (declared == null || declared.isBlank()) {
            result.addAll(AuthorExtractor.extract(
                    entry.attributeValue(OzonProductAttributes.COVER_AUTHOR), true));
        }
        return result;
    }

    private static String[] authorKeys(List<Author> authors) {
        return authors.stream().map(Author::key).distinct().toArray(String[]::new);
    }

    private static String[] authorSurnames(List<Author> authors) {
        Set<String> surnames = new LinkedHashSet<>();
        for (Author author : authors) {
            surnames.add(AuthorNormalizer.toSurname(author.raw()));
        }
        return surnames.toArray(String[]::new);
    }
}