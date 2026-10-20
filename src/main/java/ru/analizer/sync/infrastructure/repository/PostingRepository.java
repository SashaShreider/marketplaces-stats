package ru.analizer.sync.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.analizer.sync.infrastructure.entity.Posting;

import java.util.List;

public interface PostingRepository extends JpaRepository<Posting, Long> {

    /**
     * Удаляет операции перечисленных начислений.
     *
     * <p>Запрос один на весь день, а не по одному на начисление: повторная загрузка
     * дня переписывает сотни начислений, и поштучные удаления превращали её в сотни
     * же запросов к базе.
     *
     * <p>Расшифровка операции (товары и услуги доставки) уходит каскадом: у операции
     * нет строки — нет и её расшифровки.
     */
    @Modifying
    @Query("delete from Posting p where p.financeAccrual.id in :accrualIds")
    void deleteByFinanceAccrualIds(@Param("accrualIds") List<Long> accrualIds);
}
