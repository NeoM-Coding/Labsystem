package xyz.jasenon.lab.bi.telemetry;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static xyz.jasenon.lab.bi.telemetry.TelemetryMath.*;

class TelemetryMathTests {
    private final LocalDateTime day=LocalDate.of(2026,1,2).atStartOfDay();
    private Sample s(String id,LocalDateTime at,boolean opened,double energy,double power) {
        return new Sample(id,"d",at,opened,"Cooling",24,26,3,energy,power);
    }
    @Test void airSplitsHoursCapsOutagesAndWeightsOnlyRunningTime() {
        var hours=calculate(List.of(s("1",day.plusMinutes(55),true,0,0),s("2",day.plusHours(2),false,0,0)),
            day,day.plusHours(3),true,Duration.ofMinutes(15));
        assertThat(hours.stream().mapToDouble(h->h.runningSeconds).sum()).isEqualTo(900);
        assertThat(hours.stream().mapToDouble(h->h.temperatureSeconds).sum()).isEqualTo(24*900);
        assertThat(hours.get(0).runningSeconds).isEqualTo(300);
        assertThat(hours.get(1).runningSeconds).isEqualTo(600);
        assertThat(hours.stream().mapToDouble(h->h.observedSeconds).sum()).isEqualTo(1800);
    }
    @Test void energyAllocatesAcrossMidnightWithoutLosingConsumption() {
        var samples=List.of(s("1",day.minusMinutes(30),true,100,800),s("2",day.plusMinutes(30),true,102,1000));
        var before=calculate(samples,day.minusHours(1),day,false,Duration.ofMinutes(15));
        var after=calculate(samples,day,day.plusHours(1),false,Duration.ofMinutes(15));
        assertThat(before.stream().mapToDouble(h->h.energyKwh).sum()).isEqualTo(1);
        assertThat(after.stream().mapToDouble(h->h.energyKwh).sum()).isEqualTo(1);
        assertThat(after.get(0).estimated).isTrue();
        assertThat(after.get(0).peakPowerW).isEqualTo(1000);
    }
    @Test void resetIsCountedOnceAtNewReadingAndNextSegmentRestartsBaseline() {
        var samples=List.of(s("1",day.minusMinutes(10),true,200,0),s("2",day.plusMinutes(10),true,1,0),s("3",day.plusMinutes(20),true,2,0));
        var previous=calculate(samples,day.minusDays(1),day,false,Duration.ofMinutes(15));
        var current=calculate(samples,day,day.plusHours(1),false,Duration.ofMinutes(15));
        assertThat(previous.stream().mapToInt(h->h.resets).sum()).isZero();
        assertThat(current.stream().mapToInt(h->h.resets).sum()).isEqualTo(1);
        assertThat(current.stream().mapToDouble(h->h.energyKwh).sum()).isEqualTo(1);
    }
    @Test void duplicateTimestampsUseGreatestIdDeterministically() {
        var samples=List.of(s("b",day,true,10,0),s("a",day,false,100,0),s("c",day.plusMinutes(10),false,11,0));
        var first=calculate(samples,day,day.plusHours(1),true,Duration.ofMinutes(15));
        var second=calculate(List.of(samples.get(2),samples.get(1),samples.get(0)),day,day.plusHours(1),true,Duration.ofMinutes(15));
        assertThat(first.stream().mapToDouble(h->h.runningSeconds).sum()).isEqualTo(600);
        assertThat(second.stream().mapToDouble(h->h.runningSeconds).sum()).isEqualTo(600);
    }
    @Test void missingEnergyBaselineRemainsUncovered() {
        var result=calculate(List.of(s("1",day.plusMinutes(30),true,100,500)),day,day.plusHours(1),false,Duration.ofMinutes(15));
        assertThat(result.stream().mapToDouble(h->h.energyKwh).sum()).isZero();
        assertThat(result.stream().mapToDouble(h->h.energyCoveredSeconds).sum()).isZero();
    }
}
