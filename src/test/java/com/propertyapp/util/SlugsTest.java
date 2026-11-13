package com.propertyapp.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugsTest {

    @Test
    void lowercaseAsciiWithSingleHyphens() {
        assertThat(Slugs.of("home", "  Sunny Café  Home!! ", "Austin", "TX")).isEqualTo("sunny-cafe-home-austin-tx");
        assertThat(Slugs.of("home", "3/2 Ranch — Pool & Spa", null, "Texas")).isEqualTo("3-2-ranch-pool-spa-texas");
    }

    @Test
    void emptyOrSymbolOnlyTextFallsBack() {
        assertThat(Slugs.of("home", "!!!", null)).isEqualTo("home");
        assertThat(Slugs.of("agent")).isEqualTo("agent");
    }

    @Test
    void longTitlesAreCutAtAWordBoundaryAndNeverEndInAHyphen() {
        String slug = Slugs.of("home", "word ".repeat(40));
        assertThat(slug.length()).isLessThanOrEqualTo(Slugs.MAX_LENGTH);
        assertThat(slug).doesNotEndWith("-").endsWith("word");
    }

    @Test
    void pathsEndInTheId() {
        assertThat(Slugs.propertyPath("a-b", 42L)).isEqualTo("/property/a-b-42");
        assertThat(Slugs.agentPath("jane-doe", 7L)).isEqualTo("/agents/jane-doe-7");
    }
}
