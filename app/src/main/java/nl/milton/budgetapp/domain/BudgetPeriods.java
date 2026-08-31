package nl.milton.budgetapp.domain;

import java.util.Calendar;

public final class BudgetPeriods {
    private BudgetPeriods() {}

    public static final String WEEK = "WEEK";
    public static final String SALARY_PERIOD = "SALARY_PERIOD";
    public static final String MONTH = "MONTH";
    public static final String YEAR = "YEAR";
    public static final String ONE_TIME = "ONE_TIME";

    public static Range currentRange(String type, long nowMs) {
        return currentRange(type, nowMs, 23, 1);
    }

    public static Range currentRange(String type, long nowMs, int salaryStartDay, int weekStartDay) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMs);
        Calendar start = (Calendar) now.clone();
        zeroTime(start);

        switch (type == null ? MONTH : type) {
            case WEEK:
                return weekRange(start, weekStartDay);
            case SALARY_PERIOD:
                return salaryRange(start, salaryStartDay);
            case YEAR:
                Calendar yearEnd = (Calendar) start.clone();
                start.set(Calendar.DAY_OF_YEAR, 1);
                yearEnd.setTimeInMillis(start.getTimeInMillis());
                yearEnd.add(Calendar.YEAR, 1);
                yearEnd.add(Calendar.MILLISECOND, -1);
                return new Range(start.getTimeInMillis(), yearEnd.getTimeInMillis());
            case ONE_TIME:
                return new Range(0L, Long.MAX_VALUE);
            case MONTH:
            default:
                Calendar monthEnd = (Calendar) start.clone();
                start.set(Calendar.DAY_OF_MONTH, 1);
                monthEnd.setTimeInMillis(start.getTimeInMillis());
                monthEnd.add(Calendar.MONTH, 1);
                monthEnd.add(Calendar.MILLISECOND, -1);
                return new Range(start.getTimeInMillis(), monthEnd.getTimeInMillis());
        }
    }

    private static Range weekRange(Calendar today, int weekStartDay) {
        int normalized = Math.max(0, Math.min(6, weekStartDay));
        int desiredCalendarDay = normalized + 1;
        int current = today.get(Calendar.DAY_OF_WEEK);
        int delta = (7 + current - desiredCalendarDay) % 7;

        Calendar start = (Calendar) today.clone();
        start.add(Calendar.DAY_OF_MONTH, -delta);

        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, 7);
        end.add(Calendar.MILLISECOND, -1);
        return new Range(start.getTimeInMillis(), end.getTimeInMillis());
    }

    private static Range salaryRange(Calendar today, int salaryStartDay) {
        int requestedDay = Math.max(1, Math.min(31, salaryStartDay));

        Calendar currentMonthStart = (Calendar) today.clone();
        setDayClamped(currentMonthStart, requestedDay);

        Calendar start;
        if (today.getTimeInMillis() >= currentMonthStart.getTimeInMillis()) {
            start = currentMonthStart;
        } else {
            start = (Calendar) currentMonthStart.clone();
            start.add(Calendar.MONTH, -1);
            setDayClamped(start, requestedDay);
        }

        Calendar next = (Calendar) start.clone();
        next.add(Calendar.MONTH, 1);
        setDayClamped(next, requestedDay);

        Calendar end = (Calendar) next.clone();
        end.add(Calendar.MILLISECOND, -1);
        return new Range(start.getTimeInMillis(), end.getTimeInMillis());
    }

    private static void setDayClamped(Calendar c, int requestedDay) {
        c.set(Calendar.DAY_OF_MONTH, 1);
        int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        c.set(Calendar.DAY_OF_MONTH, Math.min(Math.max(1, requestedDay), max));
        zeroTime(c);
    }

    public static String displayName(String type) {
        if (WEEK.equals(type)) return "week";
        if (SALARY_PERIOD.equals(type)) return "salarisperiode";
        if (YEAR.equals(type)) return "jaar";
        if (ONE_TIME.equals(type)) return "eenmalig";
        return "maand";
    }

    private static void zeroTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    public static final class Range {
        public final long startMs;
        public final long endMs;

        public Range(long startMs, long endMs) {
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }
}
