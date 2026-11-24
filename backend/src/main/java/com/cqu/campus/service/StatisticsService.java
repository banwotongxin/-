package com.cqu.campus.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cqu.campus.common.BizException;
import com.cqu.campus.common.DateTimeUtils;
import com.cqu.campus.common.ErrorCode;
import com.cqu.campus.domain.dto.ExportFile;
import com.cqu.campus.domain.entity.Club;
import com.cqu.campus.domain.vo.ClubRankItem;
import com.cqu.campus.domain.vo.StatisticsAggregateRow;
import com.cqu.campus.domain.vo.StatisticsOverviewVO;
import com.cqu.campus.domain.vo.StatisticsTrendVO;
import com.cqu.campus.domain.vo.TrendPointVO;
import com.cqu.campus.mapper.ClubMapper;
import com.cqu.campus.mapper.StatisticsMapper;
import com.cqu.campus.security.CurrentUser;
import com.cqu.campus.security.UserContext;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 统计服务：平台管理员可跨社团，社团管理员仅限本人社团
 */
@Service
public class StatisticsService {

    public static final String RULE_VERSION = "STAT-V1";
    private static final long MAX_RANGE_DAYS = 366;
    private static final String SCOPE_ALL = "ALL";
    private static final String SCOPE_SELECTED = "SELECTED";
    private static final String SCOPE_MANAGED = "MANAGED";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String NOTE = "活动数与报名人次按创建时间统计；报名人次不含已撤销；报名人数按学生去重；"
            + "签到率 = 有效签到人数 / 已通过人数；社团活跃度 = 活动数×2 + 报名人次";
    private static final String[] ACTIVITY_STATUSES = {"DRAFT", "PUBLISHED", "FULL", "CLOSED", "ARCHIVED"};

    private final StatisticsMapper statisticsMapper;
    private final ClubMapper clubMapper;
    private final ClubService clubService;
    private final AuditService auditService;

    public StatisticsService(StatisticsMapper statisticsMapper, ClubMapper clubMapper,
                             ClubService clubService, AuditService auditService) {
        this.statisticsMapper = statisticsMapper;
        this.clubMapper = clubMapper;
        this.clubService = clubService;
        this.auditService = auditService;
    }

    public StatisticsOverviewVO overview(LocalDateTime from, LocalDateTime to, Long clubId) {
        validateRange(from, to);
        CurrentUser user = UserContext.require();
        Scope scope = resolveScope(user, clubId);
        LocalDateTime generatedAt = LocalDateTime.now();
        List<Long> clubIds = scope.ids();
        List<String> clubNames = scope.names();
        if (scope.clubs.isEmpty()) {
            return StatisticsOverviewVO.builder()
                    .activityCount(0L)
                    .publishedActivityCount(0L)
                    .activityStatusCounts(emptyStatusCounts())
                    .signupCount(0L)
                    .distinctStudentCount(0L)
                    .approvedCount(0L)
                    .checkinCount(0L)
                    .normalCheckinCount(0L)
                    .supplementCheckinCount(0L)
                    .checkinRate(0d)
                    .noBaseline(true)
                    .note("无可用社团数据，统计指标按 0 返回")
                    .ruleVersion(RULE_VERSION)
                    .scopeType(scope.scopeType)
                    .clubIds(clubIds)
                    .clubNames(clubNames)
                    .clubRanking(Collections.<ClubRankItem>emptyList())
                    .from(from)
                    .to(to)
                    .generatedAt(generatedAt)
                    .build();
        }

        Map<String, Long> statusCounts = emptyStatusCounts();
        long activityCount = 0L;
        long publishedActivityCount = 0L;
        for (StatisticsAggregateRow row : statisticsMapper.aggregateActivityByStatus(
                clubIds, scope.allClubs, from, to)) {
            long count = number(row.getActivityCount());
            activityCount += count;
            statusCounts.put(row.getStatus(), count);
            if (!STATUS_DRAFT.equals(row.getStatus())) {
                publishedActivityCount += count;
            }
        }

        StatisticsAggregateRow signupTotals = statisticsMapper.aggregateSignupTotals(
                clubIds, scope.allClubs, from, to);
        long signupCount = number(signupTotals == null ? null : signupTotals.getSignupCount());
        long distinctStudentCount = number(signupTotals == null ? null : signupTotals.getDistinctStudentCount());
        long approvedCount = number(signupTotals == null ? null : signupTotals.getApprovedCount());

        StatisticsAggregateRow signinTotals = statisticsMapper.aggregateSigninTotals(
                clubIds, scope.allClubs, from, to);
        long checkinCount = number(signinTotals == null ? null : signinTotals.getCheckinCount());
        long normalCheckinCount = number(signinTotals == null ? null : signinTotals.getNormalCheckinCount());
        long supplementCheckinCount = number(signinTotals == null ? null : signinTotals.getSupplementCheckinCount());

        boolean noBaseline = approvedCount <= 0;
        return StatisticsOverviewVO.builder()
                .activityCount(activityCount)
                .publishedActivityCount(publishedActivityCount)
                .activityStatusCounts(statusCounts)
                .signupCount(signupCount)
                .distinctStudentCount(distinctStudentCount)
                .approvedCount(approvedCount)
                .checkinCount(checkinCount)
                .normalCheckinCount(normalCheckinCount)
                .supplementCheckinCount(supplementCheckinCount)
                .checkinRate(rate(checkinCount, approvedCount))
                .noBaseline(noBaseline)
                .note(noBaseline ? NOTE + "（已通过人数为 0，签到率按 0 返回）" : NOTE)
                .ruleVersion(RULE_VERSION)
                .scopeType(scope.scopeType)
                .clubIds(clubIds)
                .clubNames(clubNames)
                .clubRanking(buildRanking(scope, from, to))
                .from(from)
                .to(to)
                .generatedAt(generatedAt)
                .build();
    }

    public StatisticsTrendVO trend(LocalDateTime from, LocalDateTime to, Long clubId) {
        validateRange(from, to);
        CurrentUser user = UserContext.require();
        Scope scope = resolveScope(user, clubId);
        LocalDateTime generatedAt = LocalDateTime.now();
        List<Long> clubIds = scope.ids();

        Map<LocalDate, TrendPointVO> pointMap = new TreeMap<LocalDate, TrendPointVO>();
        LocalDate cursor = from.toLocalDate();
        LocalDate end = to.toLocalDate();
        while (!cursor.isAfter(end)) {
            pointMap.put(cursor, TrendPointVO.builder().date(cursor.toString()).build());
            cursor = cursor.plusDays(1);
        }

        if (!scope.clubs.isEmpty()) {
            for (StatisticsAggregateRow row : statisticsMapper.trendActivity(
                    clubIds, scope.allClubs, from, to)) {
                TrendPointVO point = pointOf(pointMap, row.getStatDate());
                if (point != null) {
                    point.setActivityCount(number(row.getActivityCount()));
                }
            }
            for (StatisticsAggregateRow row : statisticsMapper.trendSignup(
                    clubIds, scope.allClubs, from, to)) {
                TrendPointVO point = pointOf(pointMap, row.getStatDate());
                if (point != null) {
                    point.setSignupCount(number(row.getSignupCount()));
                    point.setApprovedCount(number(row.getApprovedCount()));
                }
            }
            for (StatisticsAggregateRow row : statisticsMapper.trendSignin(
                    clubIds, scope.allClubs, from, to)) {
                TrendPointVO point = pointOf(pointMap, row.getStatDate());
                if (point != null) {
                    point.setCheckinCount(number(row.getCheckinCount()));
                }
            }
        }

        return StatisticsTrendVO.builder()
                .ruleVersion(RULE_VERSION)
                .scopeType(scope.scopeType)
                .clubIds(clubIds)
                .clubNames(scope.names())
                .points(new ArrayList<TrendPointVO>(pointMap.values()))
                .from(from)
                .to(to)
                .generatedAt(generatedAt)
                .build();
    }

    public ExportFile export(LocalDateTime from, LocalDateTime to, Long clubId) {
        validateRange(from, to);
        StatisticsOverviewVO overview = overview(from, to, clubId);
        StatisticsTrendVO trend = trend(from, to, clubId);
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        line(builder, "统计口径版本", RULE_VERSION);
        line(builder, "查询时间范围",
                DateTimeUtils.format(from) + " ~ " + DateTimeUtils.format(to));
        line(builder, "社团范围", overview.getClubNames().isEmpty()
                ? "无" : join(overview.getClubNames()));
        line(builder, "生成时间", DateTimeUtils.format(overview.getGeneratedAt()));
        builder.append("\r\n");
        line(builder, "指标", "数值");
        line(builder, "活动数", String.valueOf(overview.getActivityCount()));
        line(builder, "已发布活动数", String.valueOf(overview.getPublishedActivityCount()));
        for (Map.Entry<String, Long> entry : overview.getActivityStatusCounts().entrySet()) {
            line(builder, "活动状态-" + entry.getKey(), String.valueOf(entry.getValue()));
        }
        line(builder, "报名人次", String.valueOf(overview.getSignupCount()));
        line(builder, "报名人数", String.valueOf(overview.getDistinctStudentCount()));
        line(builder, "通过人数", String.valueOf(overview.getApprovedCount()));
        line(builder, "签到人数", String.valueOf(overview.getCheckinCount()));
        line(builder, "正常签到", String.valueOf(overview.getNormalCheckinCount()));
        line(builder, "补签", String.valueOf(overview.getSupplementCheckinCount()));
        line(builder, "签到率", rateText(overview));
        builder.append("\r\n");
        line(builder, "社团活跃度排行");
        line(builder, "排名", "社团名称", "活动数", "报名人次", "通过人数", "签到人数", "签到率", "活跃度");
        List<ClubRankItem> ranking = overview.getClubRanking();
        for (int i = 0; i < ranking.size(); i++) {
            ClubRankItem item = ranking.get(i);
            line(builder, String.valueOf(i + 1), item.getClubName(),
                    String.valueOf(item.getActivityCount()),
                    String.valueOf(item.getSignupCount()),
                    String.valueOf(item.getApprovedCount()),
                    String.valueOf(item.getCheckinCount()),
                    rateText(item.getCheckinRate()),
                    String.valueOf(item.getActivityScore()));
        }
        builder.append("\r\n");
        line(builder, "按日趋势");
        line(builder, "日期", "活动数", "报名人次", "通过人数", "签到人数");
        for (TrendPointVO point : trend.getPoints()) {
            line(builder, point.getDate(),
                    String.valueOf(point.getActivityCount()),
                    String.valueOf(point.getSignupCount()),
                    String.valueOf(point.getApprovedCount()),
                    String.valueOf(point.getCheckinCount()));
        }

        Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("from", DateTimeUtils.format(from));
        snapshot.put("to", DateTimeUtils.format(to));
        snapshot.put("clubId", clubId);
        snapshot.put("scopeType", overview.getScopeType());
        snapshot.put("ruleVersion", RULE_VERSION);
        auditService.record("EXPORT_STATISTICS", "STATISTICS", null, null, snapshot, "SUCCESS");

        String fileName = "statistics-" + from.toLocalDate() + "-" + to.toLocalDate() + ".csv";
        return new ExportFile(fileName, builder.toString());
    }

    private List<ClubRankItem> buildRanking(Scope scope, LocalDateTime from, LocalDateTime to) {
        List<Long> clubIds = scope.ids();
        Map<Long, StatisticsAggregateRow> activityByClub = index(statisticsMapper.aggregateActivityByClub(
                clubIds, scope.allClubs, from, to));
        Map<Long, StatisticsAggregateRow> signupByClub = index(statisticsMapper.aggregateSignupByClub(
                clubIds, scope.allClubs, from, to));
        Map<Long, StatisticsAggregateRow> signinByClub = index(statisticsMapper.aggregateSigninByClub(
                clubIds, scope.allClubs, from, to));

        List<ClubRankItem> ranking = new ArrayList<ClubRankItem>();
        for (Club club : scope.clubs) {
            StatisticsAggregateRow activityRow = activityByClub.get(club.getId());
            StatisticsAggregateRow signupRow = signupByClub.get(club.getId());
            StatisticsAggregateRow signinRow = signinByClub.get(club.getId());
            long activityCount = number(activityRow == null ? null : activityRow.getActivityCount());
            long signupCount = number(signupRow == null ? null : signupRow.getSignupCount());
            long approvedCount = number(signupRow == null ? null : signupRow.getApprovedCount());
            long checkinCount = number(signinRow == null ? null : signinRow.getCheckinCount());
            ranking.add(ClubRankItem.builder()
                    .clubId(club.getId())
                    .clubName(club.getName())
                    .activityCount(activityCount)
                    .signupCount(signupCount)
                    .approvedCount(approvedCount)
                    .checkinCount(checkinCount)
                    .checkinRate(rate(checkinCount, approvedCount))
                    .activityScore(activityCount * 2 + signupCount)
                    .build());
        }
        Collections.sort(ranking, new Comparator<ClubRankItem>() {
            @Override
            public int compare(ClubRankItem left, ClubRankItem right) {
                int compare = Long.compare(right.getActivityScore(), left.getActivityScore());
                if (compare != 0) {
                    return compare;
                }
                compare = Long.compare(right.getSignupCount(), left.getSignupCount());
                if (compare != 0) {
                    return compare;
                }
                return Long.compare(left.getClubId(), right.getClubId());
            }
        });
        return ranking;
    }

    private void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new BizException(ErrorCode.QUERY_RANGE_INVALID, "请选择统计起止时间");
        }
        if (from.isAfter(to)) {
            throw new BizException(ErrorCode.QUERY_RANGE_INVALID, "统计开始时间不能晚于结束时间");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new BizException(ErrorCode.QUERY_RANGE_INVALID, "统计时间跨度不能超过 366 天");
        }
    }

    private Scope resolveScope(CurrentUser user, Long clubId) {
        if (user.hasRole(ClubService.ROLE_PLATFORM_ADMIN)) {
            if (clubId != null) {
                Club club = clubMapper.selectById(clubId);
                if (club == null) {
                    throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "社团不存在");
                }
                return new Scope(Collections.singletonList(club), false, SCOPE_SELECTED);
            }
            List<Club> clubs = clubMapper.selectList(new QueryWrapper<Club>().orderByAsc("id"));
            return new Scope(clubs, true, SCOPE_ALL);
        }
        if (!user.hasRole(ClubService.ROLE_CLUB_ADMIN)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "仅管理员可查看统计");
        }
        List<Club> managed = clubService.listManagedClubs();
        if (clubId == null) {
            return new Scope(managed, false, SCOPE_MANAGED);
        }
        for (Club club : managed) {
            if (club.getId().equals(clubId)) {
                return new Scope(Collections.singletonList(club), false, SCOPE_MANAGED);
            }
        }
        throw new BizException(ErrorCode.AUTH_FORBIDDEN, "只能查看本人负责社团的统计");
    }

    private Map<Long, StatisticsAggregateRow> index(List<StatisticsAggregateRow> rows) {
        Map<Long, StatisticsAggregateRow> result = new HashMap<Long, StatisticsAggregateRow>();
        for (StatisticsAggregateRow row : rows) {
            if (row.getClubId() != null) {
                result.put(row.getClubId(), row);
            }
        }
        return result;
    }

    private Map<String, Long> emptyStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<String, Long>();
        for (String status : ACTIVITY_STATUSES) {
            counts.put(status, 0L);
        }
        return counts;
    }

    private TrendPointVO pointOf(Map<LocalDate, TrendPointVO> points, String statDate) {
        if (statDate == null || statDate.length() < 10) {
            return null;
        }
        try {
            return points.get(LocalDate.parse(statDate.substring(0, 10)));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private long number(Long value) {
        return value == null ? 0L : value;
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0d;
        }
        return Math.round((double) numerator / denominator * 10000d) / 100d;
    }

    private String rateText(StatisticsOverviewVO overview) {
        String text = rateText(overview.getCheckinRate());
        return overview.isNoBaseline() ? text + "（无基数）" : text;
    }

    private String rateText(double rate) {
        return rate + "%";
    }

    private String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append('、');
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private void line(StringBuilder builder, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(csv(cells[i]));
        }
        builder.append("\r\n");
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        // 防 CSV 公式注入：以 = + - @ 或制表符/回车开头的单元格前置单引号
        String safe = value;
        char first = safe.isEmpty() ? ' ' : safe.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            safe = "'" + safe;
        }
        boolean needQuote = safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r");
        String escaped = safe.replace("\"", "\"\"");
        return needQuote ? "\"" + escaped + "\"" : escaped;
    }

    /**
     * 授权范围：clubId 为空时，平台管理员取全平台，社团管理员取本人管理社团
     */
    private static class Scope {

        private final List<Club> clubs;
        private final boolean allClubs;
        private final String scopeType;

        Scope(List<Club> clubs, boolean allClubs, String scopeType) {
            this.clubs = clubs;
            this.allClubs = allClubs;
            this.scopeType = scopeType;
        }

        List<Long> ids() {
            List<Long> ids = new ArrayList<Long>();
            for (Club club : clubs) {
                ids.add(club.getId());
            }
            return ids;
        }

        List<String> names() {
            List<String> names = new ArrayList<String>();
            for (Club club : clubs) {
                names.add(club.getName());
            }
            return names;
        }
    }
}
