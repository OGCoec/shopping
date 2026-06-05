import com.ip2location.IP2Location;
import com.ip2location.IPResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class Ip2LocationWildcardLookup {

    private static final long DEFAULT_MAX_CANDIDATES = 65_536L;

    private Ip2LocationWildcardLookup() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            printUsage();
            System.exit(2);
        }

        Path dbPath = Path.of(args[0]).toAbsolutePath().normalize();
        String pattern = args[1] == null ? "" : args[1].trim();
        String targetCity = args.length >= 3 ? clean(args[2]) : "";
        String targetRegion = args.length >= 4 ? clean(args[3]) : "";

        if (targetRegion.isEmpty() && targetCity.contains(",")) {
            String[] parts = targetCity.split(",", 2);
            targetCity = clean(parts[0]);
            targetRegion = clean(parts[1]);
        }

        if (!Files.isRegularFile(dbPath)) {
            throw new IllegalArgumentException("BIN file not found: " + dbPath);
        }

        int[] octets = parsePattern(pattern);
        long candidateCount = candidateCount(octets);
        long maxCandidates = Long.getLong("iplookup.maxCandidates", DEFAULT_MAX_CANDIDATES);
        if (candidateCount > maxCandidates) {
            throw new IllegalArgumentException(
                    "Too many candidates: " + candidateCount
                            + ". Narrow the pattern or run java with -Diplookup.maxCandidates=" + candidateCount);
        }

        boolean filtered = !targetCity.isEmpty() || !targetRegion.isEmpty();
        Stats stats = new Stats(candidateCount, filtered);

        IP2Location ip2Location = new IP2Location();
        try {
            ip2Location.Open(dbPath.toString(), true);
            printHeader(pattern, dbPath, targetCity, targetRegion, candidateCount);
            search(ip2Location, octets, 0, targetCity, targetRegion, stats);
            printSummary(stats);
        } finally {
            ip2Location.Close();
        }
    }

    private static void search(IP2Location ip2Location,
                               int[] octets,
                               int index,
                               String targetCity,
                               String targetRegion,
                               Stats stats) throws Exception {
        if (index == octets.length) {
            queryOne(ip2Location, octets, targetCity, targetRegion, stats);
            return;
        }

        if (octets[index] == -1) {
            for (int value = 0; value <= 255; value++) {
                octets[index] = value;
                search(ip2Location, octets, index + 1, targetCity, targetRegion, stats);
            }
            octets[index] = -1;
            return;
        }

        search(ip2Location, octets, index + 1, targetCity, targetRegion, stats);
    }

    private static void queryOne(IP2Location ip2Location,
                                 int[] octets,
                                 String targetCity,
                                 String targetRegion,
                                 Stats stats) throws Exception {
        String ip = octets[0] + "." + octets[1] + "." + octets[2] + "." + octets[3];
        stats.queried++;

        IPResult result = ip2Location.IPQuery(ip);
        if (result == null || !"OK".equalsIgnoreCase(clean(result.getStatus()))) {
            return;
        }

        stats.ok++;
        String city = usableText(result.getCity());
        String region = usableText(result.getRegion());
        if (!matches(city, targetCity) || !matches(region, targetRegion)) {
            return;
        }

        stats.matched++;
        System.out.println(String.join("\t",
                ip,
                usableText(result.getCountryShort()),
                usableText(result.getCountryLong()),
                region,
                city,
                usableText(result.getDistrict()),
                Float.toString(result.getLatitude()),
                Float.toString(result.getLongitude())));
    }

    private static int[] parsePattern(String pattern) {
        String[] parts = pattern.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("IPv4 pattern must have 4 octets, for example: 66.93.67.*");
        }

        int[] octets = new int[4];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if ("*".equals(part)) {
                octets[i] = -1;
                continue;
            }

            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    throw new IllegalArgumentException("IPv4 octet out of range: " + part);
                }
                octets[i] = value;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid IPv4 octet: " + part);
            }
        }
        return octets;
    }

    private static long candidateCount(int[] octets) {
        long count = 1L;
        for (int octet : octets) {
            if (octet == -1) {
                count *= 256L;
            }
        }
        return count;
    }

    private static boolean matches(String actual, String expected) {
        return expected.isEmpty() || actual.equalsIgnoreCase(expected);
    }

    private static String usableText(String value) {
        String text = clean(value);
        String normalized = text.replace('_', ' ');
        if (text.isEmpty()
                || "-".equals(text)
                || "N/A".equalsIgnoreCase(text)
                || normalized.toLowerCase(Locale.ROOT).contains("not supported")) {
            return "";
        }
        return text;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static void printHeader(String pattern,
                                    Path dbPath,
                                    String targetCity,
                                    String targetRegion,
                                    long candidateCount) {
        System.out.println("Pattern: " + pattern);
        System.out.println("BIN: " + dbPath);
        System.out.println("Candidates: " + candidateCount);
        if (!targetCity.isEmpty() || !targetRegion.isEmpty()) {
            System.out.println("Filter city: " + emptyAsAny(targetCity) + ", region: " + emptyAsAny(targetRegion));
        }
        System.out.println("IP\tCountryShort\tCountryLong\tRegion\tCity\tDistrict\tLatitude\tLongitude");
    }

    private static String emptyAsAny(String value) {
        return value.isEmpty() ? "*" : value;
    }

    private static void printSummary(Stats stats) {
        System.out.println("Summary: queried=" + stats.queried
                + ", ok=" + stats.ok
                + ", matched=" + stats.matched
                + ", candidates=" + stats.candidates);
        if (stats.filtered && stats.matched == 0) {
            System.out.println("No matching IPs found for the requested city/region.");
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java Ip2LocationWildcardLookup <db-bin-path> <ipv4-pattern> [city] [region]");
        System.out.println("Example: java Ip2LocationWildcardLookup IP2LOCATION-LITE-DB11.IPV6.BIN 66.93.67.* \"San Jose\" \"California\"");
    }

    private static final class Stats {
        private final long candidates;
        private final boolean filtered;
        private long queried;
        private long ok;
        private long matched;

        private Stats(long candidates, boolean filtered) {
            this.candidates = candidates;
            this.filtered = filtered;
        }
    }
}
