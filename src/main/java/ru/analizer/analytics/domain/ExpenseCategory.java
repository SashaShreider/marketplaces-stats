package ru.analizer.analytics.domain;

import java.util.List;
import java.util.Set;

/**
 * Крупные статьи расходов, в которых продавцу удобно видеть отчёт.
 *
 * <p>Маркетплейс отдаёт расходы по <b>типам начислений</b> — их больше сотни, и
 * смотреть на них можно только в расшифровке. Карточка отчёта показывает шесть
 * статей, а расшифровка внутри статьи — те самые типы.
 *
 * <h2>Почему по названию, а не по номеру типа</h2>
 * Справочник типов обновляется из API ({@code /v1/finance/accrual/types}), поэтому
 * номера приходят с сервера и могут появиться новые. Названия типов — служебные
 * идентификаторы маркетплейса ({@code PayPerClick}, {@code Placements}), они
 * стабильны, и по ним же приходят подписи в отчёте. Захардкоженный список
 * {@code type_id} рано или поздно разъехался бы с данными молча.
 *
 * <p>Неизвестное название не выбрасывается, а попадает в {@link #OTHER}: новый тип
 * начисления OZON обязан быть виден в отчёте, пусть и не в своей статье. Пропустить
 * его значило бы потерять расход и разойтись с «к выплате».
 *
 * <h2>Что здесь намеренно не перечислено</h2>
 * Брендовые начисления ({@code BrandCommission}, {@code BrandPromotion},
 * {@code BrandShelf}), платные подписки на отзывы и кэшбэк
 * ({@code CustomerReviews}, {@code PremiumCashbackIndividualPoints}) в
 * «Продвижение» не входят: это отдельные услуги, и складывать их с оплатой за клик
 * значило бы показывать продавцу неправду о цене рекламы. Они видны в «Прочих».
 */
public enum ExpenseCategory {

    /** Комиссия за продажу. Не приходит отдельным начислением — лежит в строке товара. */
    COMMISSION("Комиссия"),

    /** Платное продвижение и подписки, влияющие на видимость товара. */
    PROMOTION("Продвижение"),

    /**
     * Логистика: все услуги доставки.
     *
     * <p>Определяется не по названию типа, а по месту, из которого пришло начисление
     * ({@link FeeFact.FeeKind#DELIVERY}): у логистики десяток типов, и список меняется.
     */
    LOGISTICS("Логистика"),

    /** Размещение, приёмка и обработка товара на складе. */
    STORAGE("Хранение и обработка"),

    /** Эквайринг — комиссия за приём платежа. */
    ACQUIRING("Эквайринг"),

    /** Всё, что не названо отдельной статьёй. */
    OTHER("Прочие расходы");

    /**
     * Названия типов начислений OZON, которые входят в статью.
     *
     * <p>Сравнение без учёта регистра: справочник приходит от маркетплейса, и
     * полагаться на точное совпадение регистра в чужом API нельзя.
     */
    private static final Set<String> PROMOTION_TYPES = Set.of(
            "PayPerClick",
            "Promotion",
            "FirstCustomerReview",
            "StarsMembership",
            "PremiumSubscription");

    private static final Set<String> STORAGE_TYPES = Set.of(
            "Placements",
            "CrossDock",
            "ItemPacking",
            "SupplyInbound");

    private static final Set<String> ACQUIRING_TYPES = Set.of(
            "Acquiring");

    private final String label;

    ExpenseCategory(String label) {
        this.label = label;
    }

    /** Подпись для интерфейса и выгрузки. */
    public String label() {
        return label;
    }

    /** Порядок строк в карточке расходов и в таблице. */
    public static List<ExpenseCategory> displayOrder() {
        return List.of(values());
    }

    /**
     * Статья для начисления по названию его типа.
     *
     * @param typeName служебное название из справочника, например {@code PayPerClick};
     *                 {@code null} для строки, у которой типа нет
     * @return статья; {@link #OTHER}, если название не перечислено выше
     */
    public static ExpenseCategory byTypeName(String typeName) {
        if (typeName == null) {
            return OTHER;
        }
        if (contains(PROMOTION_TYPES, typeName)) {
            return PROMOTION;
        }
        if (contains(STORAGE_TYPES, typeName)) {
            return STORAGE;
        }
        if (contains(ACQUIRING_TYPES, typeName)) {
            return ACQUIRING;
        }
        return OTHER;
    }

    private static boolean contains(Set<String> names, String typeName) {
        for (String name : names) {
            if (name.equalsIgnoreCase(typeName)) {
                return true;
            }
        }
        return false;
    }
}
