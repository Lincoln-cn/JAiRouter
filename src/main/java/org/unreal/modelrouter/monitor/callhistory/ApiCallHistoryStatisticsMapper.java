package org.unreal.modelrouter.monitor.callhistory;

import lombok.extern.slf4j.Slf4j;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryStatisticsDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * API 调用历史统计结果映射器
 * 将数据库查询结果安全映射为 DTO 对象
 *
 * @author JAiRouter Team
 * @since 2.9.1
 */
@Slf4j
public final class ApiCallHistoryStatisticsMapper {

    private ApiCallHistoryStatisticsMapper() {
    }

    /**
     * 按模型分组统计映射
     */
    public static List<CallHistoryStatisticsDTO.ModelStats> mapByModel(final List<Object[]> rows) {
        List<CallHistoryStatisticsDTO.ModelStats> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 5) {
                continue;
            }
            try {
                long reqCount = toLong(row[1]);
                long successCount = toLong(row[4]);
                result.add(CallHistoryStatisticsDTO.ModelStats.builder()
                        .modelName(String.valueOf(row[0]))
                        .requestCount(reqCount)
                        .totalTokens(toLong(row[2]))
                        .avgResponseTimeMs(toDouble(row[3]))
                        .successCount(successCount)
                        .successRate(reqCount > 0 ? (double) successCount / reqCount * 100 : 0.0)
                        .build());
            } catch (Exception e) {
                log.debug("Skipping invalid model stats row: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 按服务类型分组统计映射
     */
    public static List<CallHistoryStatisticsDTO.ServiceTypeStats> mapByServiceType(final List<Object[]> rows) {
        List<CallHistoryStatisticsDTO.ServiceTypeStats> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 4) {
                continue;
            }
            try {
                result.add(CallHistoryStatisticsDTO.ServiceTypeStats.builder()
                        .serviceType(String.valueOf(row[0]))
                        .requestCount(toLong(row[1]))
                        .totalTokens(toLong(row[2]))
                        .avgResponseTimeMs(toDouble(row[3]))
                        .build());
            } catch (Exception e) {
                log.debug("Skipping invalid service type stats row: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 按日分组统计映射
     */
    public static List<CallHistoryStatisticsDTO.DailyStats> mapByDay(final List<Object[]> rows) {
        List<CallHistoryStatisticsDTO.DailyStats> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 3) {
                continue;
            }
            try {
                result.add(CallHistoryStatisticsDTO.DailyStats.builder()
                        .date(String.valueOf(row[0]))
                        .requestCount(toLong(row[1]))
                        .totalTokens(toLong(row[2]))
                        .build());
            } catch (Exception e) {
                log.debug("Skipping invalid daily stats row: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 按小时分组统计映射
     */
    public static List<CallHistoryStatisticsDTO.HourlyStats> mapByHour(final List<Object[]> rows) {
        List<CallHistoryStatisticsDTO.HourlyStats> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 2) {
                continue;
            }
            try {
                int hour = toLong(row[0]).intValue();
                result.add(CallHistoryStatisticsDTO.HourlyStats.builder()
                        .hour(hour)
                        .requestCount(toLong(row[1]))
                        .label(String.format("%02d:00", hour))
                        .build());
            } catch (Exception e) {
                log.debug("Skipping invalid hourly stats row: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 按状态码分组统计映射
     */
    public static List<CallHistoryStatisticsDTO.StatusCodeStats> mapByStatusCode(final List<Object[]> rows) {
        List<CallHistoryStatisticsDTO.StatusCodeStats> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 2) {
                continue;
            }
            try {
                result.add(CallHistoryStatisticsDTO.StatusCodeStats.builder()
                        .statusCode(toLong(row[0]).intValue())
                        .count(toLong(row[1]))
                        .build());
            } catch (Exception e) {
                log.debug("Skipping invalid status code stats row: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 按错误码分组统计映射
     */
    public static List<CallHistoryStatisticsDTO.ErrorCodeStats> mapByErrorCode(final List<Object[]> rows) {
        List<CallHistoryStatisticsDTO.ErrorCodeStats> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 2) {
                continue;
            }
            try {
                result.add(CallHistoryStatisticsDTO.ErrorCodeStats.builder()
                        .errorCode(String.valueOf(row[0]))
                        .count(toLong(row[1]))
                        .build());
            } catch (Exception e) {
                log.debug("Skipping invalid error code stats row: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 安全转换为 Long
     */
    public static Long toLong(final Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    /**
     * 安全转换为 Double
     */
    public static Double toDouble(final Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }
}
