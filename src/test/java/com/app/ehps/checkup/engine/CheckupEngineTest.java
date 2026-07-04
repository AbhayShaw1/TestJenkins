package com.app.ehps.checkup.engine;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exhaustive pin tests for {@link CheckupEngine} — the safety net for the crown-jewel
 * checkup scoring logic (BEHAVIOR-BASELINE.md §9). Every (type, param) combination is
 * covered with a good/warning/bad value plus exact boundary values, cross-checked against
 * the legacy {@code com.app.ehps_api.service.TechnicianCheckupService} private methods.
 *
 * No Spring context — CheckupEngine is a pure, framework-free class.
 */
class CheckupEngineTest {

    private final CheckupEngine engine = new CheckupEngine();

    // ------------------------------------------------------------------
    // parameterCount
    // ------------------------------------------------------------------

    @Nested
    class ParameterCount {

        @Test
        void type1And2Have5Params() {
            assertThat(engine.parameterCount(1)).isEqualTo(5);
            assertThat(engine.parameterCount(2)).isEqualTo(5);
        }

        @Test
        void types3To6Have4Params() {
            assertThat(engine.parameterCount(3)).isEqualTo(4);
            assertThat(engine.parameterCount(4)).isEqualTo(4);
            assertThat(engine.parameterCount(5)).isEqualTo(4);
            assertThat(engine.parameterCount(6)).isEqualTo(4);
        }

        @Test
        void unknownTypeIsZero() {
            assertThat(engine.parameterCount(0)).isEqualTo(0);
            assertThat(engine.parameterCount(7)).isEqualTo(0);
            assertThat(engine.parameterCount(-1)).isEqualTo(0);
        }
    }

    // ------------------------------------------------------------------
    // classify — Type 1 Lithography
    // ------------------------------------------------------------------

    @Nested
    class Lithography {

        // P1 Light Intensity: good[80,100], warn[60,80), bad<60, fall-through -> warning
        @ParameterizedTest
        @CsvSource({
                "90, good",
                "80, good",
                "100, good",
                "79.9, warning",
                "60, warning",
                "59, bad",
                "100.1, warning" // fall-through default
        })
        void p1(float v, String expected) {
            assertThat(engine.classify(1, 1, v)).isEqualTo(expected);
        }

        // P2 Lens Temp: good[20,25], warn[26,28], bad>28, gap (25,26) -> warning fall-through
        @ParameterizedTest
        @CsvSource({
                "22, good",
                "20, good",
                "25, good",
                "27, warning",
                "26, warning",
                "28, warning",
                "28.1, bad",
                "25.5, warning" // gap fall-through
        })
        void p2(float v, String expected) {
            assertThat(engine.classify(1, 2, v)).isEqualTo(expected);
        }

        // P3 Stage Vibration: good<3, warn[3,5], bad>5
        @ParameterizedTest
        @CsvSource({
                "1, good",
                "2.9, good",
                "3, warning",
                "5, warning",
                "5.1, bad"
        })
        void p3(float v, String expected) {
            assertThat(engine.classify(1, 3, v)).isEqualTo(expected);
        }

        // P4 Reticle Alignment Err: good<2, warn[2,4], bad>4
        @ParameterizedTest
        @CsvSource({
                "1, good",
                "1.9, good",
                "2, warning",
                "4, warning",
                "4.1, bad"
        })
        void p4(float v, String expected) {
            assertThat(engine.classify(1, 4, v)).isEqualTo(expected);
        }

        // P5 Focus Accuracy: good<10, warn[10,20], bad>20
        @ParameterizedTest
        @CsvSource({
                "5, good",
                "9.9, good",
                "10, warning",
                "20, warning",
                "20.1, bad"
        })
        void p5(float v, String expected) {
            assertThat(engine.classify(1, 5, v)).isEqualTo(expected);
        }

        @Test
        void unknownParamIsBad() {
            assertThat(engine.classify(1, 6, 50f)).isEqualTo("bad");
            assertThat(engine.classify(1, 0, 50f)).isEqualTo("bad");
        }
    }

    // ------------------------------------------------------------------
    // classify — Type 2 Etcher
    // ------------------------------------------------------------------

    @Nested
    class Etcher {

        // P1 RF Plasma Power: good[450,500], warn[400,450), bad<400, fall-through -> warning
        @ParameterizedTest
        @CsvSource({
                "470, good",
                "450, good",
                "500, good",
                "449.9, warning",
                "400, warning",
                "399, bad",
                "500.1, warning"
        })
        void p1(float v, String expected) {
            assertThat(engine.classify(2, 1, v)).isEqualTo(expected);
        }

        // P2 Chamber Pressure: good[20,30], warn[31,40], bad else
        @ParameterizedTest
        @CsvSource({
                "25, good",
                "20, good",
                "30, good",
                "35, warning",
                "31, warning",
                "40, warning",
                "40.1, bad",
                "30.5, bad" // gap falls to bad here (no fall-through warning branch)
        })
        void p2(float v, String expected) {
            assertThat(engine.classify(2, 2, v)).isEqualTo(expected);
        }

        // P3 Chamber Temp: good[60,80], warn[81,90], bad else
        @ParameterizedTest
        @CsvSource({
                "70, good",
                "60, good",
                "80, good",
                "85, warning",
                "81, warning",
                "90, warning",
                "90.1, bad",
                "80.5, bad"
        })
        void p3(float v, String expected) {
            assertThat(engine.classify(2, 3, v)).isEqualTo(expected);
        }

        // P4 Gas Flow Rate: good[80,100], warn[60,80), bad<60, fall-through -> warning
        @ParameterizedTest
        @CsvSource({
                "90, good",
                "80, good",
                "100, good",
                "79.9, warning",
                "60, warning",
                "59, bad",
                "100.1, warning"
        })
        void p4(float v, String expected) {
            assertThat(engine.classify(2, 4, v)).isEqualTo(expected);
        }

        // P5 Etch Rate: good[100,120], warn[85,100), bad<85, fall-through -> warning
        @ParameterizedTest
        @CsvSource({
                "110, good",
                "100, good",
                "120, good",
                "99.9, warning",
                "85, warning",
                "84, bad",
                "120.1, warning"
        })
        void p5(float v, String expected) {
            assertThat(engine.classify(2, 5, v)).isEqualTo(expected);
        }

        @Test
        void unknownParamIsBad() {
            assertThat(engine.classify(2, 6, 50f)).isEqualTo("bad");
        }
    }

    // ------------------------------------------------------------------
    // classify — Type 3 CVD
    // ------------------------------------------------------------------

    @Nested
    class Cvd {

        // P1 Chamber Temp: good[300,350], warn[351,370], bad else
        @ParameterizedTest
        @CsvSource({
                "320, good",
                "300, good",
                "350, good",
                "360, warning",
                "351, warning",
                "370, warning",
                "370.1, bad",
                "350.5, bad"
        })
        void p1(float v, String expected) {
            assertThat(engine.classify(3, 1, v)).isEqualTo(expected);
        }

        // P2 Gas Flow: good[100,150], warn[80,100), bad<80, fall-through -> warning
        @ParameterizedTest
        @CsvSource({
                "120, good",
                "100, good",
                "150, good",
                "99.9, warning",
                "80, warning",
                "79, bad",
                "150.1, warning"
        })
        void p2(float v, String expected) {
            assertThat(engine.classify(3, 2, v)).isEqualTo(expected);
        }

        // P3 Vacuum Pressure: good[0.5,1], warn(1,2], bad else
        @ParameterizedTest
        @CsvSource({
                "0.7, good",
                "0.5, good",
                "1, good",
                "1.5, warning",
                "2, warning",
                "2.1, bad",
                "0.4, bad"
        })
        void p3(float v, String expected) {
            assertThat(engine.classify(3, 3, v)).isEqualTo(expected);
        }

        // P4 Deposition Uniformity: good>95, warn[90,95], bad else
        @ParameterizedTest
        @CsvSource({
                "97, good",
                "95.1, good",
                "93, warning",
                "90, warning",
                "95, warning",
                "89.9, bad"
        })
        void p4(float v, String expected) {
            assertThat(engine.classify(3, 4, v)).isEqualTo(expected);
        }

        @Test
        void unknownParamIsBad() {
            assertThat(engine.classify(3, 5, 50f)).isEqualTo("bad");
        }
    }

    // ------------------------------------------------------------------
    // classify — Type 4 Ion Implanter
    // ------------------------------------------------------------------

    @Nested
    class IonImplanter {

        // P1 Beam Current: good[10,12], warn[8,10), bad<8, fall-through -> warning
        @ParameterizedTest
        @CsvSource({
                "11, good",
                "10, good",
                "12, good",
                "9.9, warning",
                "8, warning",
                "7.9, bad",
                "12.1, warning"
        })
        void p1(float v, String expected) {
            assertThat(engine.classify(4, 1, v)).isEqualTo(expected);
        }

        // P2 Beam Energy: good[40,50], warn[30,40), bad<30, fall-through -> warning
        @ParameterizedTest
        @CsvSource({
                "45, good",
                "40, good",
                "50, good",
                "39.9, warning",
                "30, warning",
                "29.9, bad",
                "50.1, warning"
        })
        void p2(float v, String expected) {
            assertThat(engine.classify(4, 2, v)).isEqualTo(expected);
        }

        // P3 Vacuum Pressure: good<=1e-6, warn<=1e-5, bad>1e-4, GAP (1e-5, 1e-4] -> warning fall-through
        @ParameterizedTest
        @CsvSource({
                "0.0000005, good",
                "0.000001, good",
                "0.00001, warning",
                "0.000005, warning",
                "0.00005, warning", // the documented gap case (5e-5)
                "0.0001, warning",  // upper edge of gap: v>0.0001 is false at exactly 0.0001 -> fall-through
                "0.00011, bad"
        })
        void p3Gap(float v, String expected) {
            assertThat(engine.classify(4, 3, v)).isEqualTo(expected);
        }

        // P4 Cooling Temp: good[18,22], warn[23,25], bad>25, gap (22,23) -> warning fall-through
        @ParameterizedTest
        @CsvSource({
                "20, good",
                "18, good",
                "22, good",
                "24, warning",
                "23, warning",
                "25, warning",
                "25.1, bad",
                "22.5, warning" // gap fall-through
        })
        void p4(float v, String expected) {
            assertThat(engine.classify(4, 4, v)).isEqualTo(expected);
        }

        @Test
        void unknownParamIsBad() {
            assertThat(engine.classify(4, 5, 50f)).isEqualTo("bad");
        }
    }

    // ------------------------------------------------------------------
    // classify — Type 5 CMP
    // ------------------------------------------------------------------

    @Nested
    class Cmp {

        // P1 Slurry Flow: good[150,180], warn[120,150), bad<120, fall-through -> warning
        @ParameterizedTest
        @CsvSource({
                "160, good",
                "150, good",
                "180, good",
                "149.9, warning",
                "120, warning",
                "119.9, bad",
                "180.1, warning"
        })
        void p1(float v, String expected) {
            assertThat(engine.classify(5, 1, v)).isEqualTo(expected);
        }

        // P2 Pad Pressure: good[3,5], warn(5,6], bad>6, gap below 3 -> warning fall-through
        @ParameterizedTest
        @CsvSource({
                "4, good",
                "3, good",
                "5, good",
                "5.5, warning",
                "6, warning",
                "6.1, bad",
                "2.9, warning" // fall-through: not good, not warning(>5 false), not bad(>6 false)
        })
        void p2(float v, String expected) {
            assertThat(engine.classify(5, 2, v)).isEqualTo(expected);
        }

        // P3 Platen Speed: good[60,80], warn[81,95], bad else
        @ParameterizedTest
        @CsvSource({
                "70, good",
                "60, good",
                "80, good",
                "90, warning",
                "81, warning",
                "95, warning",
                "95.1, bad",
                "80.5, bad"
        })
        void p3(float v, String expected) {
            assertThat(engine.classify(5, 3, v)).isEqualTo(expected);
        }

        // P4 Pad Temp: good[25,35], warn[36,40], bad else
        @ParameterizedTest
        @CsvSource({
                "30, good",
                "25, good",
                "35, good",
                "38, warning",
                "36, warning",
                "40, warning",
                "40.1, bad",
                "35.5, bad"
        })
        void p4(float v, String expected) {
            assertThat(engine.classify(5, 4, v)).isEqualTo(expected);
        }

        @Test
        void unknownParamIsBad() {
            assertThat(engine.classify(5, 5, 50f)).isEqualTo("bad");
        }
    }

    // ------------------------------------------------------------------
    // classify — Type 6 Inspection
    // ------------------------------------------------------------------

    @Nested
    class Inspection {

        // P1 Laser Power: good[90,100], warn[75,90), bad<75, fall-through -> warning
        @ParameterizedTest
        @CsvSource({
                "95, good",
                "90, good",
                "100, good",
                "89.9, warning",
                "75, warning",
                "74.9, bad",
                "100.1, warning"
        })
        void p1(float v, String expected) {
            assertThat(engine.classify(6, 1, v)).isEqualTo(expected);
        }

        // P2 Sensor Calibration: good[0,2], warn[3,5], bad else
        @ParameterizedTest
        @CsvSource({
                "1, good",
                "0, good",
                "2, good",
                "4, warning",
                "3, warning",
                "5, warning",
                "5.1, bad",
                "2.5, bad"
        })
        void p2(float v, String expected) {
            assertThat(engine.classify(6, 2, v)).isEqualTo(expected);
        }

        // P3 Focus Error: good[0,10], warn[11,20], bad else
        @ParameterizedTest
        @CsvSource({
                "5, good",
                "0, good",
                "10, good",
                "15, warning",
                "11, warning",
                "20, warning",
                "20.1, bad",
                "10.5, bad"
        })
        void p3(float v, String expected) {
            assertThat(engine.classify(6, 3, v)).isEqualTo(expected);
        }

        // P4 Defect Count: good<10, warn[10,50], bad>50
        @ParameterizedTest
        @CsvSource({
                "5, good",
                "9.9, good",
                "10, warning",
                "50, warning",
                "50.1, bad"
        })
        void p4(float v, String expected) {
            assertThat(engine.classify(6, 4, v)).isEqualTo(expected);
        }

        @Test
        void unknownParamIsBad() {
            assertThat(engine.classify(6, 5, 50f)).isEqualTo("bad");
        }
    }

    @Test
    void unknownTypeIdIsBad() {
        assertThat(engine.classify(0, 1, 50f)).isEqualTo("bad");
        assertThat(engine.classify(7, 1, 50f)).isEqualTo("bad");
        assertThat(engine.classify(-1, 1, 50f)).isEqualTo("bad");
    }

    // ------------------------------------------------------------------
    // finalHealth
    // ------------------------------------------------------------------

    @Nested
    class FinalHealth {

        @Test
        void allGoodFiveParamIs100() {
            String[] statuses = {"good", "good", "good", "good", "good"};
            assertThat(engine.finalHealth(statuses)).isEqualTo(100);
        }

        @Test
        void allGoodFourParamIs100() {
            String[] statuses = {"good", "good", "good", "good"};
            assertThat(engine.finalHealth(statuses)).isEqualTo(100);
        }

        @Test
        void allBadIsZero() {
            assertThat(engine.finalHealth(new String[]{"bad", "bad", "bad", "bad", "bad"})).isEqualTo(0);
            assertThat(engine.finalHealth(new String[]{"bad", "bad", "bad", "bad"})).isEqualTo(0);
        }

        @Test
        void mixedFiveParam_3good2warning_is80() {
            // weight = 20; 3*20 + 2*10 = 60 + 20 = 80
            String[] statuses = {"good", "good", "good", "warning", "warning"};
            assertThat(engine.finalHealth(statuses)).isEqualTo(80);
        }

        @Test
        void mixedFourParam_roundsHalfUp_to38() {
            // weight = 25; 1 good + 1 warning + 2 bad = 25 + 12.5 + 0 + 0 = 37.5 -> round -> 38
            String[] statuses = {"good", "warning", "bad", "bad"};
            assertThat(engine.finalHealth(statuses)).isEqualTo(38);
        }
    }

    // ------------------------------------------------------------------
    // evaluate — orchestration, alert/severity rules
    // ------------------------------------------------------------------

    @Nested
    class Evaluate {

        @Test
        void alertNeeded_whenBadCountPositive_severityHigh() {
            // type 1 (litho, 5 params): P1=59(bad), rest good
            float[] values = {59f, 22f, 1f, 1f, 5f};
            CheckupEvaluation result = engine.evaluate(1, values);

            assertThat(result.getStatuses()).containsExactly("bad", "good", "good", "good", "good");
            assertThat(result.getBadCount()).isEqualTo(1);
            assertThat(result.getWarningCount()).isEqualTo(0);
            assertThat(result.isAlertNeeded()).isTrue();
            assertThat(result.getSeverity()).isEqualTo("high");
        }

        @Test
        void alertNeeded_whenWarningCountExceeds3_andNoBad_severityMedium() {
            // type 1 (litho, 5 params): 4 warnings, 1 good, no bad
            // P1=70 warning, P2=27 warning, P3=4 warning, P4=3 warning, P5=5 good
            float[] values = {70f, 27f, 4f, 3f, 5f};
            CheckupEvaluation result = engine.evaluate(1, values);

            assertThat(result.getStatuses()).containsExactly("warning", "warning", "warning", "warning", "good");
            assertThat(result.getBadCount()).isEqualTo(0);
            assertThat(result.getWarningCount()).isEqualTo(4);
            assertThat(result.isAlertNeeded()).isTrue();
            assertThat(result.getSeverity()).isEqualTo("medium");
        }

        @Test
        void noAlert_whenWarningCountAtMost3_andNoBad_severityNull() {
            // type 1 (litho, 5 params): exactly 3 warnings, 2 good, no bad
            // P1=70 warning, P2=27 warning, P3=4 warning, P4=1 good, P5=5 good
            float[] values = {70f, 27f, 4f, 1f, 5f};
            CheckupEvaluation result = engine.evaluate(1, values);

            assertThat(result.getStatuses()).containsExactly("warning", "warning", "warning", "good", "good");
            assertThat(result.getBadCount()).isEqualTo(0);
            assertThat(result.getWarningCount()).isEqualTo(3);
            assertThat(result.isAlertNeeded()).isFalse();
            assertThat(result.getSeverity()).isNull();
        }

        @Test
        void evaluate_computesFinalHealthConsistentlyWithStatuses() {
            // type 3 (CVD, 4 params), all good -> finalHealth 100
            float[] values = {320f, 120f, 0.7f, 97f};
            CheckupEvaluation result = engine.evaluate(3, values);

            assertThat(result.getStatuses()).containsExactly("good", "good", "good", "good");
            assertThat(result.getFinalHealth()).isEqualTo(100);
            assertThat(result.isAlertNeeded()).isFalse();
            assertThat(result.getSeverity()).isNull();
        }
    }
}
