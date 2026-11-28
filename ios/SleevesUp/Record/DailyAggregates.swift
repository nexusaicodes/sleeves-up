import GRDB
import Shared

/// A habit's days as the shared `DailyAggregate`, read with Android's `getDailyAggregates` query:
/// completed sessions only, one row per `date_key` that holds one, so a key is present exactly when
/// the day counts. Each ending's count is matched against `ClosedBy`'s stored values.
enum DailyAggregates {

    static func fetch(_ db: Database, habitId: Int64, from startKey: String, through endKey: String) throws
        -> [DailyAggregate] {
        let endings = [ClosedBy.dayBoundary, .inApp, .timerNotification, .reminderNotification, .widget]
            .map(\.storedValue)
        let rows = try Row.fetchAll(
            db,
            sql: """
                SELECT date_key,
                       COALESCE(SUM(duration), 0) AS total,
                       COUNT(*) AS count,
                       MIN(started_at) AS first_check_in,
                       MAX(stopped_at) AS last_check_out,
                       COALESCE(SUM(CASE WHEN closed_by = ? THEN 1 ELSE 0 END), 0) AS day_boundary,
                       COALESCE(SUM(CASE WHEN closed_by = ? THEN 1 ELSE 0 END), 0) AS in_app,
                       COALESCE(SUM(CASE WHEN closed_by = ? THEN 1 ELSE 0 END), 0) AS timer,
                       COALESCE(SUM(CASE WHEN closed_by = ? THEN 1 ELSE 0 END), 0) AS reminder,
                       COALESCE(SUM(CASE WHEN closed_by = ? THEN 1 ELSE 0 END), 0) AS widget
                FROM sessions
                WHERE habit_id = ?
                  AND date_key BETWEEN ? AND ?
                  AND stopped_at IS NOT NULL
                GROUP BY date_key
                ORDER BY date_key ASC
                """,
            arguments: StatementArguments(endings + [habitId, startKey, endKey] as [any DatabaseValueConvertible])
        )
        return rows.map { row in
            DailyAggregate(
                dateKey: row["date_key"],
                totalDurationMs: row["total"],
                sessionCount: row["count"],
                firstCheckIn: row["first_check_in"],
                lastCheckOut: (row["last_check_out"] as Int64?).map { KotlinLong(value: $0) },
                dayBoundaryCheckOuts: row["day_boundary"],
                inAppCheckOuts: row["in_app"],
                timerNotificationCheckOuts: row["timer"],
                reminderNotificationCheckOuts: row["reminder"],
                widgetCheckOuts: row["widget"]
            )
        }
    }

    /// The `date_key`s, as the shared code keys a record's days.
    static func byDateKey(_ days: [DailyAggregate]) -> [String: DailyAggregate] {
        Dictionary(uniqueKeysWithValues: days.map { ($0.dateKey, $0) })
    }
}
