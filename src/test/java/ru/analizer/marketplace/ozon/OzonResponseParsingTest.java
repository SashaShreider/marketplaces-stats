package ru.analizer.marketplace.ozon;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import ru.analizer.marketplace.AccrualDto;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Разбор реального ответа OZON {@code /v1/finance/accrual/by-day} за 2026-04-10.
 * Фикстура — снятый с продавца ответ, а не придуманный вручную JSON.
 */
class OzonResponseParsingTest {

    private static final Path FIXTURE = Path.of("example-2026-04-10.json");

    private static final tools.jackson.databind.ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static String rawJson() throws IOException {
        try (InputStream in = Files.newInputStream(FIXTURE)) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Test
    void parsesRealResponseWithoutLosingAccruals() throws Exception {
        String raw = rawJson();
        var root = MAPPER.readTree(raw);

        assertThat(root.has("accruals")).isTrue();
        assertThat(root.get("accruals")).isInstanceOf(tools.jackson.databind.node.ArrayNode.class);
        assertThat(root.get("accruals").size()).isEqualTo(94);
        // Курсор пустой — день вычитан полностью одной страницей.
        assertThat(root.get("last_id").asString()).isEmpty();
    }

    @Test
    void mapsPostingAccrualWithCommissionAndDelivery() throws Exception {
        AccrualDto dto = findByExternalId(48762627746L);

        assertThat(dto.category()).isEqualTo(AccrualDto.Category.POSTING);
        assertThat(dto.unitNumber()).isEqualTo("31020767-0597-1");
        assertThat(dto.totalAmount()).isEqualByComparingTo("869.30");
        assertThat(dto.currency()).isEqualTo("RUB");

        AccrualDto.Posting posting = dto.posting();
        assertThat(posting).isNotNull();
        assertThat(posting.deliverySchema()).isEqualTo("Fbo");
        assertThat(posting.products()).hasSize(1);

        AccrualDto.Product product = posting.products().getFirst();
        assertThat(product.sku()).isEqualTo(1388985440L);
        assertThat(product.quantity()).isEqualTo(1);

        AccrualDto.Commission commission = product.commission();
        assertThat(commission).isNotNull();
        assertThat(commission.salePrice()).isEqualByComparingTo("871.43");
        assertThat(commission.sellerPrice()).isEqualByComparingTo("1530");
        assertThat(commission.saleAmount()).isEqualByComparingTo("1530");
        // Комиссия хранится со знаком минус — это расход.
        assertThat(commission.saleCommission()).isEqualByComparingTo("-596.70");
        assertThat(commission.commission()).isEqualByComparingTo("-596.70");
        assertThat(commission.coinvestment()).isEqualByComparingTo("8.71");
        assertThat(commission.bonus()).isEqualByComparingTo("649.86");
        // commission_ratio приходит как "value:\"0.390000\"" — извлекаем само значение.
        assertThat(commission.commissionRatio()).isEqualTo("0.390000");

        assertThat(product.deliveryTotalAccrued()).isEqualByComparingTo("-64.00");
        assertThat(product.deliveryServices()).hasSize(2);
        assertThat(product.deliveryServices().get(0).typeId()).isEqualTo(32);
        assertThat(product.deliveryServices().get(0).amount()).isEqualByComparingTo("-56.94");
    }

    @Test
    void mapsPostingAccrualWithoutCommission() throws Exception {
        AccrualDto dto = findByExternalId(48751325983L);

        assertThat(dto.category()).isEqualTo(AccrualDto.Category.POSTING);
        AccrualDto.Product product = dto.posting().products().getFirst();
        // Начисление только по логистике: commission отсутствует, но delivery есть.
        assertThat(product.commission()).isNull();
        assertThat(product.deliveryServices()).isNotEmpty();
        assertThat(product.deliveryTotalAccrued()).isEqualByComparingTo("-67.11");
    }

    @Test
    void mapsItemAccrual() throws Exception {
        AccrualDto dto = findByExternalId(48762253857L);

        assertThat(dto.category()).isEqualTo(AccrualDto.Category.ITEM);
        assertThat(dto.unitNumber()).isEqualTo("02321694-0488");
        assertThat(dto.posting()).isNull();
        assertThat(dto.itemFees()).hasSize(1);

        AccrualDto.ItemFee itemFee = dto.itemFees().getFirst();
        assertThat(itemFee.sku()).isEqualTo(3753845770L);
        assertThat(itemFee.quantity()).isEqualTo(1);
        assertThat(itemFee.fees()).hasSize(1);
        assertThat(itemFee.fees().getFirst().typeId()).isEqualTo(1);
        assertThat(itemFee.fees().getFirst().amount()).isEqualByComparingTo("-14.75");
    }

    @Test
    void mapsNonItemAccrualWithoutUnitNumber() throws Exception {
        AccrualDto dto = findByExternalId(48822624708L);

        assertThat(dto.category()).isEqualTo(AccrualDto.Category.NON_ITEM);
        // У части NON_ITEM начислений unit_number отсутствует.
        assertThat(dto.unitNumber()).isNull();
        assertThat(dto.itemFees()).isNull();
        assertThat(dto.nonItemFee()).isNotNull();
        assertThat(dto.nonItemFee().typeId()).isEqualTo(46);
        assertThat(dto.nonItemFee().amount()).isEqualByComparingTo("-380.68");
    }

    @Test
    void keepsRawJsonForEveryAccrual() throws Exception {
        AccrualDto dto = findByExternalId(48762627746L);
        assertThat(dto.rawJson()).isNotBlank();

        // Исходный JSON обязан оставаться разбираемым и содержать то же accrual_id.
        var reparsed = MAPPER.readTree(dto.rawJson());
        assertThat(reparsed.get("accrual_id").asLong()).isEqualTo(48762627746L);
        assertThat(reparsed.get("accrued_category").asString()).isEqualTo("POSTING");
    }

    @Test
    void sameUnitNumberHasSeveralAccruals() throws Exception {
        List<AccrualDto> forUnit = all().stream()
                .filter(d -> "04142187-0153-1".equals(d.unitNumber()))
                .toList();

        // Одна продажа — несколько финансовых операций: 1 POSTING + 3 ITEM.
        assertThat(forUnit).hasSize(4);
        assertThat(forUnit.stream().map(AccrualDto::category))
                .containsExactlyInAnyOrder(AccrualDto.Category.POSTING,
                        AccrualDto.Category.ITEM, AccrualDto.Category.ITEM, AccrualDto.Category.ITEM);
        assertThat(forUnit.stream().map(AccrualDto::externalId).distinct()).hasSize(4);

        AccrualDto posting = forUnit.stream()
                .filter(d -> d.category() == AccrualDto.Category.POSTING)
                .findFirst().orElseThrow();
        assertThat(posting.totalAmount()).isEqualByComparingTo("595.47");

        // Все три ITEM-операции относятся к тому же SKU, что и продажа.
        Long postingSku = posting.posting().products().getFirst().sku();
        for (AccrualDto item : forUnit.stream().filter(d -> d.category() == AccrualDto.Category.ITEM).toList()) {
            assertThat(item.itemFees().getFirst().sku()).isEqualTo(postingSku);
        }
    }

    @Test
    void containsSaleReturnAndExpenseScenarios() throws Exception {
        // Возврат: отрицательная sale_price.
        List<AccrualDto> returns = all().stream()
                .filter(d -> d.posting() != null)
                .filter(d -> d.posting().products().stream()
                        .anyMatch(p -> p.commission() != null
                                && p.commission().salePrice() != null
                                && p.commission().salePrice().signum() < 0))
                .toList();
        assertThat(returns).isNotEmpty();

        AccrualDto.Product returned = returns.getFirst().posting().products().getFirst();
        assertThat(returned.commission().salePrice()).isEqualByComparingTo("-1039.05");
        assertThat(returned.commission().saleAmount()).isEqualByComparingTo("-1724.00");
        assertThat(returned.commission().bonus()).isEqualByComparingTo("-674.56");
        // При возврате комиссия возвращается продавцу, то есть становится положительной.
        assertThat(returned.commission().saleCommission()).isEqualByComparingTo("672.36");
        assertThat(returned.sku()).isEqualTo(1973850522L);

        // Продажи: положительная sale_price.
        assertThat(all().stream()
                .filter(d -> d.posting() != null)
                .flatMap(d -> d.posting().products().stream())
                .filter(p -> p.commission() != null && p.commission().salePrice() != null)
                .filter(p -> p.commission().salePrice().signum() > 0)
                .count()).isEqualTo(19);

        // Комиссия повторяет знак операции: при продаже — расход, при возврате — возврат комиссии.
        // Это проверено на реальных данных, а не предположено: наивное «sale_commission всегда
        // отрицательная» сломало бы учёт возвратов.
        assertThat(all().stream()
                .filter(d -> d.posting() != null)
                .flatMap(d -> d.posting().products().stream())
                .filter(p -> p.commission() != null && p.commission().salePrice() != null)
                .filter(p -> p.commission().salePrice().signum() > 0)
                .allMatch(p -> p.commission().saleCommission().signum() < 0)).isTrue();

        assertThat(all().stream()
                .filter(d -> d.posting() != null)
                .flatMap(d -> d.posting().products().stream())
                .filter(p -> p.commission() != null && p.commission().salePrice() != null)
                .filter(p -> p.commission().salePrice().signum() < 0)
                .allMatch(p -> p.commission().saleCommission().signum() > 0)).isTrue();

        // sale_commision и commission совпадают по значению.
        assertThat(all().stream()
                .filter(d -> d.posting() != null)
                .flatMap(d -> d.posting().products().stream())
                .filter(p -> p.commission() != null && p.commission().commission() != null)
                .allMatch(p -> p.commission().commission()
                        .compareTo(p.commission().saleCommission()) == 0)).isTrue();

        // Логистика: у товаров с продажей есть хотя бы одна услуга доставки.
        assertThat(all().stream()
                .filter(d -> d.posting() != null)
                .flatMap(d -> d.posting().products().stream())
                .filter(p -> p.commission() != null && p.commission().salePrice() != null
                        && p.commission().salePrice().signum() > 0)
                .allMatch(p -> !p.deliveryServices().isEmpty())).isTrue();

        // При этом у начисления по возврату блок delivery приходит null целиком —
        // десериализатор обязан это переживать, а не ронять разбор.
        AccrualDto returnAccrual = all().stream()
                .filter(d -> d.posting() != null)
                .filter(d -> d.posting().products().stream()
                        .anyMatch(p -> p.commission() != null
                                && p.commission().salePrice() != null
                                && p.commission().salePrice().signum() < 0))
                .findFirst().orElseThrow();
        AccrualDto.Product returnedProduct = returnAccrual.posting().products().getFirst();
        assertThat(returnedProduct.deliveryTotalAccrued()).isNull();
        assertThat(returnedProduct.deliveryServices()).isEmpty();

        // Прочие расходы: NON_ITEM без привязки к SKU.
        assertThat(all().stream().anyMatch(d -> d.category() == AccrualDto.Category.NON_ITEM)).isTrue();
    }

    @Test
    void accrualLevelTypeIdIsAbsentInRealResponse() throws Exception {
        // Документация OZON объявляет type_id на уровне операции, но реально его нет:
        // тип начисления лежит в детализации. Это зафиксировано тестом, чтобы при появлении
        // поля мы узнали об этом, а не получили бы молчаливую потерю данных.
        assertThat(all().stream().allMatch(d -> d.typeId() == null)).isTrue();
    }

    @Test
    void totalAmountSumsMatchManualCalculation() throws Exception {
        // Ручная сверка: сумма total_amount за день по сохранённому файлу.
        BigDecimal sum = all().stream()
                .map(AccrualDto::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("11297.23");
    }

    private static List<AccrualDto> all() throws IOException {
        String raw = rawJson();
        var root = MAPPER.readTree(raw);
        List<AccrualDto> result = new java.util.ArrayList<>();
        for (var node : root.get("accruals")) {
            result.add(OzonMapper.toAccrualDto(MAPPER.treeToValue(node, ru.analizer.marketplace.ozon.dto.FinanceAccrual.class),
                    node.toString()));
        }
        return result;
    }

    private static AccrualDto findByExternalId(Long externalId) throws IOException {
        return all().stream()
                .filter(d -> externalId.equals(d.externalId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("accrual " + externalId + " не найден в фикстуре"));
    }
}
