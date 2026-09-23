package net.tfminecraft.research.util;

/**
 * Progress pane math for aspect rows (cols 4-8).
 *
 * <p>Always 5 active panes when requiredPoints &gt; 0.
 * Pane k (1-based) fills when currentPoints &gt;= ceil(k * required / 5).
 * Examples: 10 needed -> thresholds 2/4/6/8/10; 3 needed at 3/3 -> all 5 panes lit.
 */
public final class AspectProgressLayout {

    private AspectProgressLayout() {}

    public static int activePaneCount(int requiredPoints) {
        if (requiredPoints <= 0) {
            return 0;
        }
        return GridLayout.ASPECT_PROGRESS_PANES;
    }

    public static boolean isPaneActive(int paneIndex, int requiredPoints) {
        if (paneIndex < 0 || paneIndex >= GridLayout.ASPECT_PROGRESS_PANES) {
            return false;
        }
        return requiredPoints > 0;
    }

    public static int paneThreshold(int paneIndex, int requiredPoints) {
        if (!isPaneActive(paneIndex, requiredPoints)) {
            return 0;
        }
        int paneNumber = paneIndex + 1;
        return (int) Math.ceil((paneNumber * requiredPoints) / (double) GridLayout.ASPECT_PROGRESS_PANES);
    }

    public static boolean isPaneFilled(int paneIndex, int currentPoints, int requiredPoints) {
        if (!isPaneActive(paneIndex, requiredPoints)) {
            return false;
        }
        return currentPoints >= paneThreshold(paneIndex, requiredPoints);
    }

    public static int filledPaneCount(int currentPoints, int requiredPoints) {
        int count = 0;
        for (int pane = 0; pane < GridLayout.ASPECT_PROGRESS_PANES; pane++) {
            if (isPaneFilled(pane, currentPoints, requiredPoints)) {
                count++;
            }
        }
        return count;
    }
}
