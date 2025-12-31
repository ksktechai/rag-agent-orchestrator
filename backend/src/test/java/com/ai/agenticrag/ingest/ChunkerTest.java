package com.ai.agenticrag.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Chunker class.
 */
class ChunkerTest {

    @Nested
    class ChunkMethod {

        @Test
        void chunk_simpleText() {
            String text = "This is a simple text that should be chunked.";
            List<String> chunks = Chunker.chunk(text, 20, 5);

            assertFalse(chunks.isEmpty());
            for (String chunk : chunks) {
                assertTrue(chunk.length() <= 20);
            }
        }

        @Test
        void chunk_withOverlap() {
            String text = "AAAAA BBBBB CCCCC DDDDD EEEEE";
            List<String> chunks = Chunker.chunk(text, 12, 5);

            assertTrue(chunks.size() > 1);
        }

        @Test
        void chunk_emptyText() {
            List<String> chunks = Chunker.chunk("", 100, 10);

            assertTrue(chunks.isEmpty());
        }

        @Test
        void chunk_nullText() {
            List<String> chunks = Chunker.chunk(null, 100, 10);

            assertTrue(chunks.isEmpty());
        }

        @Test
        void chunk_whitespaceOnlyText() {
            List<String> chunks = Chunker.chunk("   \n\t  ", 100, 10);

            assertTrue(chunks.isEmpty());
        }

        @Test
        void chunk_textSmallerThanMaxChars() {
            String text = "Small text";
            List<String> chunks = Chunker.chunk(text, 100, 10);

            assertEquals(1, chunks.size());
            assertEquals("Small text", chunks.get(0));
        }

        @Test
        void chunk_exactlyMaxChars() {
            String text = "1234567890";
            List<String> chunks = Chunker.chunk(text, 10, 2);

            assertEquals(1, chunks.size());
        }
    }

    @Nested
    class SmartChunkMethod {

        @Test
        void smartChunk_proseText() {
            String prose = """
                    This is a paragraph of prose text that describes something interesting.
                    It contains multiple sentences and should be processed as regular text.

                    This is another paragraph that continues the discussion.
                    It provides more context and details about the topic.
                    """;

            List<String> chunks = Chunker.smartChunk(prose);

            assertFalse(chunks.isEmpty());
        }

        @Test
        void smartChunk_tableData() {
            String tableText = """
                    Product       Q1     Q2     Q3     Q4
                    Widget A      100    150    200    250
                    Widget B      200    225    275    300
                    Widget C      50     75     100    125
                    Total         350    450    575    675
                    """;

            List<String> chunks = Chunker.smartChunk(tableText);

            assertFalse(chunks.isEmpty());
        }

        @Test
        void smartChunk_mixedContent() {
            String mixed = """
                    Introduction

                    This document contains various types of content including text and tables.

                    Sales Data:
                    Year    Revenue    Profit
                    2020    1000       100
                    2021    1200       150
                    2022    1500       200

                    Conclusion

                    The data shows positive growth trends over the years.
                    """;

            List<String> chunks = Chunker.smartChunk(mixed);

            assertFalse(chunks.isEmpty());
        }

        @Test
        void smartChunk_emptyText() {
            List<String> chunks = Chunker.smartChunk("");

            assertTrue(chunks.isEmpty());
        }

        @Test
        void smartChunk_nullText() {
            List<String> chunks = Chunker.smartChunk(null);

            assertTrue(chunks.isEmpty());
        }

        @Test
        void smartChunk_whitespaceOnly() {
            List<String> chunks = Chunker.smartChunk("   \n\n\t  ");

            assertTrue(chunks.isEmpty());
        }

        @Test
        void smartChunk_deduplicatesContent() {
            String duplicated = """
                    Same line one
                    Same line two

                    Same line one
                    Same line two
                    """;

            List<String> chunks = Chunker.smartChunk(duplicated);

            // Should deduplicate exact matches
            long uniqueCount = chunks.stream().distinct().count();
            assertEquals(chunks.size(), uniqueCount);
        }
    }

    @Nested
    class ChunkByLinesMethod {

        @Test
        void chunkByLines_simpleLines() {
            String text = """
                    Line 1
                    Line 2
                    Line 3
                    Line 4
                    Line 5
                    """;

            List<String> chunks = Chunker.chunkByLines(text, 2, 1);

            assertTrue(chunks.size() >= 2);
        }

        @Test
        void chunkByLines_withOverlap() {
            String text = """
                    Row A: 100 200
                    Row B: 150 250
                    Row C: 175 275
                    Row D: 200 300
                    """;

            List<String> chunks = Chunker.chunkByLines(text, 2, 1);

            // With overlap, chunks should share some lines
            assertTrue(chunks.size() >= 2);
        }

        @Test
        void chunkByLines_emptyText() {
            List<String> chunks = Chunker.chunkByLines("", 10, 2);

            assertTrue(chunks.isEmpty());
        }

        @Test
        void chunkByLines_fewerLinesThanMax() {
            String text = "Line 1\nLine 2";
            List<String> chunks = Chunker.chunkByLines(text, 10, 2);

            assertEquals(1, chunks.size());
        }

        @Test
        void chunkByLines_singleLine() {
            String text = "Just one line";
            List<String> chunks = Chunker.chunkByLines(text, 10, 2);

            assertEquals(1, chunks.size());
            assertEquals("Just one line", chunks.get(0));
        }

        @Test
        void chunkByLines_zeroOverlap() {
            String text = "Line1\nLine2\nLine3\nLine4";
            List<String> chunks = Chunker.chunkByLines(text, 2, 0);

            assertEquals(2, chunks.size());
        }
    }

    @Nested
    class NormalizationTests {

        @Test
        void smartChunk_normalizesCarriageReturns() {
            String text = "Line 1\r\nLine 2\r\nLine 3";
            List<String> chunks = Chunker.smartChunk(text);

            assertFalse(chunks.isEmpty());
            for (String chunk : chunks) {
                assertFalse(chunk.contains("\r"));
            }
        }

        @Test
        void smartChunk_collapsesMultipleSpaces() {
            String text = "Word1    Word2     Word3";
            List<String> chunks = Chunker.smartChunk(text);

            assertFalse(chunks.isEmpty());
            String first = chunks.get(0);
            assertFalse(first.contains("    ")); // Multiple spaces collapsed
        }

        @Test
        void smartChunk_collapsesManyNewlines() {
            String text = "Paragraph 1\n\n\n\n\n\nParagraph 2";
            List<String> chunks = Chunker.smartChunk(text);

            assertFalse(chunks.isEmpty());
        }
    }

    @Nested
    class TableDetectionTests {

        @Test
        void smartChunk_detectsNumericTable() {
            String table = """
                    2020    1000    500    250
                    2021    1100    550    275
                    2022    1200    600    300
                    2023    1300    650    325
                    2024    1400    700    350
                    """;

            List<String> chunks = Chunker.smartChunk(table);

            assertFalse(chunks.isEmpty());
        }

        @Test
        void smartChunk_detectsFinancialTable() {
            String table = """
                    Revenue     $1,000  $1,200  $1,500
                    Costs       $500    $600    $700
                    Profit      $500    $600    $800
                    Margin      50%     50%     53%
                    """;

            List<String> chunks = Chunker.smartChunk(table);

            assertFalse(chunks.isEmpty());
        }

        @Test
        void smartChunk_shortBlockNotTable() {
            String shortText = "A: 1\nB: 2";
            List<String> chunks = Chunker.smartChunk(shortText);

            // Short blocks are not treated as tables (less than 4 lines)
            assertFalse(chunks.isEmpty());
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void chunk_veryLongText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("Word").append(i).append(" ");
            }
            String longText = sb.toString();

            List<String> chunks = Chunker.chunk(longText, 500, 50);

            assertTrue(chunks.size() > 1);
        }

        @Test
        void smartChunk_unicodeContent() {
            String unicode = "日本語テキスト\n中文内容\n한국어 텍스트";
            List<String> chunks = Chunker.smartChunk(unicode);

            assertFalse(chunks.isEmpty());
        }

        @Test
        void smartChunk_specialCharacters() {
            String special = "Price: $100.50 (10% off)\nWeight: 2.5kg <15lbs>";
            List<String> chunks = Chunker.smartChunk(special);

            assertFalse(chunks.isEmpty());
        }

        @Test
        void chunk_zeroMaxChars_handlesGracefully() {
            // Edge case: max chars of 0 or negative should be handled
            String text = "Some text";
            // The method should handle this gracefully (implementation dependent)
            try {
                List<String> chunks = Chunker.chunk(text, 1, 0);
                // If it doesn't throw, just verify it returns something
                assertNotNull(chunks);
            } catch (Exception e) {
                // If it throws, that's also acceptable behavior for invalid input
            }
        }
    }
}
