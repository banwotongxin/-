package com.cqu.campus.mapper;

import com.cqu.campus.domain.vo.StatisticsAggregateRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统计只读聚合：在数据库侧完成计数，不拉取明细行
 */
public interface StatisticsMapper {

    List<StatisticsAggregateRow> aggregateActivityByStatus(@Param("clubIds") List<Long> clubIds,
                                                           @Param("allClubs") boolean allClubs,
                                                           @Param("from") LocalDateTime from,
                                                           @Param("to") LocalDateTime to);

    StatisticsAggregateRow aggregateSignupTotals(@Param("clubIds") List<Long> clubIds,
                                                 @Param("allClubs") boolean allClubs,
                                                 @Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to);

    StatisticsAggregateRow aggregateSigninTotals(@Param("clubIds") List<Long> clubIds,
                                                 @Param("allClubs") boolean allClubs,
                                                 @Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to);

    List<StatisticsAggregateRow> aggregateActivityByClub(@Param("clubIds") List<Long> clubIds,
                                                         @Param("allClubs") boolean allClubs,
                                                         @Param("from") LocalDateTime from,
                                                         @Param("to") LocalDateTime to);

    List<StatisticsAggregateRow> aggregateSignupByClub(@Param("clubIds") List<Long> clubIds,
                                                       @Param("allClubs") boolean allClubs,
                                                       @Param("from") LocalDateTime from,
                                                       @Param("to") LocalDateTime to);

    List<StatisticsAggregateRow> aggregateSigninByClub(@Param("clubIds") List<Long> clubIds,
                                                       @Param("allClubs") boolean allClubs,
                                                       @Param("from") LocalDateTime from,
                                                       @Param("to") LocalDateTime to);

    List<StatisticsAggregateRow> trendActivity(@Param("clubIds") List<Long> clubIds,
                                               @Param("allClubs") boolean allClubs,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);

    List<StatisticsAggregateRow> trendSignup(@Param("clubIds") List<Long> clubIds,
                                             @Param("allClubs") boolean allClubs,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);

    List<StatisticsAggregateRow> trendSignin(@Param("clubIds") List<Long> clubIds,
                                             @Param("allClubs") boolean allClubs,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);
}
