package com.example.bms.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Seam: the document-number format, pure and without mocks. The shape
 * api-design pins ("Numbers {@code ORD|PO|INV-YYYY-NNNN} generated
 * server-side") is decided here, in one place, so the side that mints and
 * the side that reads a stored value back cannot drift apart — the same
 * "one rule, both sides" reason {@link CheckDigits} exists.
 */
class DocumentNumberTest {

    @Test
    void mintingPadsTheSequenceToFourDigits() {
        var number = DocumentNumber.mint("ORD", 2026, 7);

        assertThat(number.value()).isEqualTo("ORD-2026-0007");
        assertThat(number.toString()).isEqualTo("ORD-2026-0007");
    }

    /** The V8 CHECK allows {@code [0-9]{4,}}: a fifth digit grows, never truncates. */
    @Test
    void aSequencePastFourDigitsGrowsInsteadOfTruncating() {
        assertThat(DocumentNumber.mint("INV", 2026, 12345).value())
                .isEqualTo("INV-2026-12345");
    }

    @Test
    void aStoredNumberReadsBackIntoTheSameValue() {
        var stored = DocumentNumber.parse("PO-2026-0042");

        assertThat(stored).isEqualTo(DocumentNumber.parse("PO-2026-0042"));
        assertThat(stored.value()).isEqualTo("PO-2026-0042");
    }

    /**
     * A stored value that is not the shape contradicts the format this class
     * owns, so it is a server bug and fails loudly rather than travelling
     * through the domain as if it were a number.
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "ORD-26-0001", "ord-2026-0001", "ORD-2026-1",
            "ORD-2026-0001x", "2026-0001", "ORD 2026 0001"})
    void aValueThatIsNotTheShapeIsRefused(String value) {
        assertThatThrownBy(() -> DocumentNumber.parse(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** The database sequence starts at 1, so nothing may mint a zero. */
    @Test
    void aNonPositiveSequenceIsRefused() {
        assertThatThrownBy(() -> DocumentNumber.mint("ORD", 2026, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sequence");
    }
}
