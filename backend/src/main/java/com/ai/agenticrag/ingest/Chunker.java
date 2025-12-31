package com.ai.agenticrag.ingest;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generic chunking utilities that work well for both prose and table-like PDF text.
 * No domain hardcoding.
 */
public final class Chunker {

    private Chunker() {}

    // Heuristics
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]{2,}");
    private static final Pattern MANY_NEWLINES = Pattern.compile("\\n{3,}");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*\\d.*");

    /**
     * Backwards-compatible: character window chunking with overlap.
     */
    public static List<String> chunk(String text, int maxChars, int overlapChars) {
        String t = normalize(text);
        if (t.isEmpty()) return List.of();

        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < t.length()) {
            int end = Math.min(t.length(), start + maxChars);
            String piece = t.substring(start, end).trim();
            if (!piece.isEmpty()) out.add(piece);

            if (end == t.length()) break;
            start = Math.max(0, end - overlapChars);
        }
        return out;
    }

    /**
     * Best default for PDFs (including tables): split into blocks, detect tables, chunk accordingly.
     *
     * Typical usage:
     *   List<String> chunks = Chunker.smartChunk(text);
     */
    public static List<String> smartChunk(String text) {
        String t = normalize(text);
        if (t.isEmpty()) return List.of();

        // Split into blocks by blank lines (paragraphs / table blocks)
        List<String> blocks = splitBlocks(t);

        List<String> out = new ArrayList<>();
        for (String block : blocks) {
            if (block.isBlank()) continue;

            // If a block looks like a table, chunk by lines (keeps rows intact)
            if (looksLikeTable(block)) {
                out.addAll(chunkByLines(block, 50, 6)); // 18 lines, overlap 4
            } else {
                // Prose-ish: keep paragraphs, but cap size
                out.addAll(chunkByCharWithParagraphBias(block, 700, 120));
            }
        }

        // Final pass: remove empties and dedupe exact duplicates while preserving order
        return dedupePreserveOrder(out.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList()));
    }

    /**
     * Normalize text that commonly comes from Tika/PDF extraction.
     * Keeps newlines (important for tables), but cleans noise.
     */
    private static String normalize(String text) {
        if (text == null) return "";
        String t = text.replace("\r", "");

        // Collapse weird spacing but preserve newlines
        t = MULTI_SPACE.matcher(t).replaceAll(" ");

        // Collapse too many newlines
        t = MANY_NEWLINES.matcher(t).replaceAll("\n\n");

        // Trim each line (helps with table alignment)
        t = Arrays.stream(t.split("\n", -1))
                .map(String::stripTrailing)
                .collect(Collectors.joining("\n"));

        return t.trim();
    }

    private static List<String> splitBlocks(String t) {
        List<String> lines = Arrays.stream(t.split("\n"))
                .map(String::trim)
                .collect(Collectors.toList());

        List<String> blocks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();

        boolean curIsTable = false;

        for (String line : lines) {
            boolean blank = line.isBlank();
            boolean tableishLine = !blank && (countNumericTokens(line) >= 2 || (HAS_DIGIT.matcher(line).matches() && line.length() < 140));

            if (cur.length() == 0) {
                // start new block
                cur.append(line);
                curIsTable = tableishLine;
                continue;
            }

            // If blank line:
            // - for prose: blank line ends block
            // - for table: blank line is ignored (tables often have blank lines between rows)
            if (blank) {
                if (!curIsTable) {
                    String b = cur.toString().trim();
                    if (!b.isEmpty()) blocks.add(b);
                    cur.setLength(0);
                    curIsTable = false;
                }
                continue;
            }

            // Non-blank line:
            // If we're in a table OR the line looks table-ish, keep merging
            if (curIsTable || tableishLine) {
                cur.append("\n").append(line);
                curIsTable = true;
            } else {
                // prose continuation
                cur.append("\n").append(line);
            }
        }

        String last = cur.toString().trim();
        if (!last.isEmpty()) blocks.add(last);

        return blocks;
    }

    /**
     * Generic table detection:
     * - multiple lines
     * - several lines containing multiple numbers
     * - relatively short lines (table rows) rather than long prose
     */
    private static boolean looksLikeTable(String block) {
        List<String> lines = Arrays.stream(block.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (lines.size() < 4) return false;

        // Count lines that have "many numbers" (>=2 numeric tokens)
        long numericRowLike = lines.stream()
                .filter(l -> countNumericTokens(l) >= 2)
                .count();

        // Table-ish blocks usually have multiple numeric rows
        if (numericRowLike >= 3) return true;

        // Also treat as table if many lines have digits and are not huge prose lines
        long linesWithDigits = lines.stream().filter(l -> HAS_DIGIT.matcher(l).matches()).count();
        double avgLen = lines.stream().mapToInt(String::length).average().orElse(0);
        return linesWithDigits >= 4 && avgLen < 120;
    }

    private static int countNumericTokens(String line) {
        // Rough tokenization: split on spaces, commas stay attached but we strip them
        String[] parts = line.split("\\s+");
        int count = 0;
        for (String p : parts) {
            String x = p.replace(",", "").replace("$", "").trim();
            // token is numeric if it contains digits and is mostly number-ish
            if (!x.isEmpty() && x.matches("-?\\d+(\\.\\d+)?")) {
                count++;
            }
        }
        return count;
    }

    /**
     * Chunk a block by lines (best for tables). Keeps line breaks.
     */
    public static List<String> chunkByLines(String text, int maxLines, int overlapLines) {
        String t = normalize(text);
        if (t.isEmpty()) return List.of();

        List<String> lines = Arrays.stream(t.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (lines.isEmpty()) return List.of();

        List<String> out = new ArrayList<>();
        int start = 0;

        int step = Math.max(1, maxLines - Math.max(0, overlapLines));

        while (start < lines.size()) {
            int end = Math.min(lines.size(), start + maxLines);
            String chunk = String.join("\n", lines.subList(start, end)).trim();
            if (!chunk.isEmpty()) out.add(chunk);
            if (end == lines.size()) break;
            start += step;
        }

        return out;
    }

    /**
     * Prose-friendly chunking: keep paragraph content, but cap size.
     */
    private static List<String> chunkByCharWithParagraphBias(String block, int maxChars, int overlapChars) {
        // If already small, keep as-is
        if (block.length() <= maxChars) return List.of(block);

        // Otherwise fall back to window chunking
        return chunk(block, maxChars, overlapChars);
    }

    private static List<String> dedupePreserveOrder(List<String> in) {
        LinkedHashSet<String> set = new LinkedHashSet<>(in);
        return new ArrayList<>(set);
    }
}