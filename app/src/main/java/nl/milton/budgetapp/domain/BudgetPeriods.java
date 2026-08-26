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
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMs);
        Calendar start = (Calendar) now.clone();
        Calendar end = (Calendar) now.clone();
        zeroTime(start);
        zeroTime(end);

        switch (type == null ? MONTH : type) {
            case WEEK:
                int first = start.getFirstDayOfWeek();
                int current = start.get(Calendar.DAY_OF_WEEK);
                int delta = (7 + current - first) % 7;
                start.add(Calendar.DAY_OF_MONTH, -delta);
                end.setTimeInMillis(start.getTimeInMillis());
                end.add(Calendar.DAY_OF_MONTH, 7);
                end.add(Calendar.MILLISECOND, -1);
                break;
            case SALARY_PERIOD:
                if (start.get(Calendar.DAY_OF_MONTH) >= 23) {
                    start.set(Calendar.DAY_OF_MONTH, 23);
                    end.setTimeInMillis(start.getTimeInMillis());
                    end.add(Calendar.MONTH, 1);
                    end.set(Calendar.DAY_OF_MONTH, 23);
                    end.add(Calendar.MILLISECOND, -1);
                } else {
                    end.set(Calendar.DAY_OF_MONTH, 23);
                    end.add(Calendar.MILLISECOND, -1);
                    start.add(Calendar.MONTH, -1);
                    start.set(Calendar.DAY_OF_MONTH, 23);
                }
                break;
            case YEAR:
                start.set(Calendar.DAY_OF_YEAR, 1);
                end.setTimeInMillis(start.getTimeInMillis());
                end.add(Calendar.YEAR, 1);
                end.add(Calendar.MILLISECOND, -1);
                break;
            case ONE_TIME:
                return new Range(0L, Long.MAX_VALUE);
            case MONTH:
            default:
                start.set(Calendar.DAY_OF_MONTH, 1);
                end.setTimeInMillis(start.getTimeInMillis());
                end.add(Calendar.MONTH, 1);
                end.add(Calendar.MILLISECOND, -1);
                break;
        }
        return new Range(start.getTimeInMillis(), end.getTimeInMillis());
    }

    public static String displayName(String type) {
        if (WEEK.equals(type)) return "week";
        if (SALARY_PERIOD.equals(type)) return "salarisperiode (23e–22e)";
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
