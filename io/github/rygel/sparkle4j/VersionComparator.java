package io.github.rygel.sparkle4j;

import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/**
 * SemVer 2.0 comparator.
 *
 * <ul>
 *   <li>Numeric comparison per segment (not lexicographic).
 *   <li>Pre-release qualifiers (-alpha, -beta, -rc.1) sort below the release version.
 * </ul>
 */
public final class VersionComparator implements Comparator<String>, java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** Shared singleton instance. */
    public static final VersionComparator INSTANCE = new VersionComparator();

    private VersionComparator() {}

    /**
     * Returns true if {@code candidate} is strictly newer than {@code current}.
     *
     * @param candidate the version to test
     * @param current the currently installed version
     * @return {@code true} if candidate is newer
     */
    public static boolean isNewer(String candidate, String current) {
        return INSTANCE.compare(candidate, current) > 0;
    }

    @Override
    public int compare(String a, String b) {
        var pa = parse(a);
        var pb = parse(b);

        for (int i = 0; i < 3; i++) {
            int diff = pa.numbers[i] - pb.numbers[i];
            if (diff != 0) return diff;
        }

        if (pa.preRelease == null && pb.preRelease == null) return 0;
        if (pa.preRelease == null) return 1; // release > pre-release
        if (pb.preRelease == null) return -1; // pre-release < release
        return pa.preRelease.compareTo(pb.preRelease);
    }

    private record Parsed(int[] numbers, @Nullable String preRelease) {}

    private static Parsed parse(String version) {
        var parts = version.trim().split("-", 2);
        var segments = parts[0].split("\\.");
        var numbers = new int[3];
        for (int i = 0; i < 3; i++) {
            numbers[i] = i < segments.length ? parseIntOrZero(segments[i]) : 0;
        }
        var preRelease = parts.length > 1 ? parts[1] : null;
        return new Parsed(numbers, preRelease);
    }

    private static int parseIntOrZero(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
