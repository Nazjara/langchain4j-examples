package com.nazjara.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nazjara.client.OpenMeteoClient;
import com.nazjara.model.Place;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceFinderTest {

    private static final Place LVIV = new Place("Lviv", "Lviv Oblast", "Ukraine", "UA", 49.84, 24.02, "Europe/Kyiv", 717_803L);
    private static final Place LVIV_VILLAGE = new Place("Lviv", "Rivne Oblast", "Ukraine", "UA", 50.6, 26.2, "Europe/Kyiv", 300L);
    private static final Place SPRINGFIELD_IL = new Place("Springfield", "Illinois", "United States", "US", 39.8, -89.6, "America/Chicago", 116_250L);
    private static final Place SPRINGFIELD_MO = new Place("Springfield", "Missouri", "United States", "US", 37.2, -93.3, "America/Chicago", 166_810L);
    private static final Place SPRINGFIELD_GARDENS = new Place("Springfield Gardens", "New York", "United States", "US", 40.7, -73.8, "America/New_York", 15_000L);

    @Mock
    private OpenMeteoClient openMeteoClient;

    private PlaceFinder placeFinder;

    @BeforeEach
    void setUp() {
        placeFinder = new PlaceFinder(openMeteoClient);
    }

    @Test
    void dominantPlaceIsChosenWithoutAsking() {
        when(openMeteoClient.findPlaces("Lviv")).thenReturn(List.of(LVIV_VILLAGE, LVIV));

        var place = placeFinder.choose("Lviv", null, null);

        assertThat(place).isEqualTo(LVIV);
    }

    @Test
    void similarSizedPlacesAreAmbiguousAndListedLargestFirst() {
        when(openMeteoClient.findPlaces("Springfield")).thenReturn(List.of(SPRINGFIELD_IL, SPRINGFIELD_GARDENS, SPRINGFIELD_MO));

        assertThatThrownBy(() -> placeFinder.choose("Springfield", null, null))
                .isInstanceOf(AmbiguousPlaceException.class)
                .hasMessageContaining("Springfield, Missouri, United States; Springfield, Illinois, United States.")
                .hasMessageNotContaining("Gardens");
    }

    @Test
    void regionNarrowsTheChoiceToOnePlace() {
        when(openMeteoClient.findPlaces("Springfield")).thenReturn(List.of(SPRINGFIELD_IL, SPRINGFIELD_MO));

        var place = placeFinder.choose("Springfield", "US", "illinois");

        assertThat(place).isEqualTo(SPRINGFIELD_IL);
    }

    @Test
    void fuzzyMatchesAloneAreNotAPlace() {
        when(openMeteoClient.findPlaces("Springfield")).thenReturn(List.of(SPRINGFIELD_GARDENS));

        assertThatThrownBy(() -> placeFinder.choose("Springfield", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No place named Springfield was found");
    }
}
