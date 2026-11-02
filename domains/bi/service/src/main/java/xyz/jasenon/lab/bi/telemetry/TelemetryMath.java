package xyz.jasenon.lab.bi.telemetry;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import xyz.jasenon.lab.bi.api.view.TelemetryDashboard.Fault;

/** All intervals are half open. No database or wall clock access. */
public final class TelemetryMath {
    private TelemetryMath() {}
    public record Sample(String id, String deviceId, LocalDateTime time, boolean opened, String mode,
                         double temperature, double roomTemperature, int errorCode, double energy, double power) {}
    public static class Hour {
        public LocalDateTime hour;
        public double expectedSeconds, observedSeconds, runningSeconds, temperatureSeconds, roomTemperatureSeconds;
        public double energyKwh, energyCoveredSeconds, peakPowerW;
        public int resets;
        public boolean estimated;
        public Map<String, Double> modes = new TreeMap<>();
        public List<Fault> faults = new ArrayList<>();
        public Hour() {}
        public Hour(LocalDateTime time) { hour = time; }
    }
    public static List<Hour> calculate(List<Sample> input, LocalDateTime start, LocalDateTime end,
                                       boolean air, Duration staleAfter) {
        TreeMap<LocalDateTime, Sample> unique = new TreeMap<>();
        input.stream().sorted(Comparator.comparing(Sample::time).thenComparing(Sample::id))
                .forEach(sample -> unique.put(sample.time(), sample));
        List<Sample> samples = new ArrayList<>(unique.values());
        Map<LocalDateTime, Hour> hours = new TreeMap<>();
        for (LocalDateTime cursor=start;cursor.isBefore(end);) {
            LocalDateTime key=cursor.truncatedTo(ChronoUnit.HOURS);
            LocalDateTime stop=key.plusHours(1).isBefore(end)?key.plusHours(1):end;
            hours.computeIfAbsent(key,Hour::new).expectedSeconds=seconds(cursor,stop);
            cursor=stop;
        }
        for (int i = 0; i < samples.size(); i++) {
            Sample a = samples.get(i), next = i + 1 < samples.size() ? samples.get(i + 1) : null;
            LocalDateTime stop = next == null ? end : next.time();
            if (air && stop.isAfter(a.time().plus(staleAfter))) stop = a.time().plus(staleAfter);
            LocalDateTime from = a.time().isBefore(start) ? start : a.time();
            LocalDateTime to = stop.isAfter(end) ? end : stop;
            if (!from.isBefore(to)) continue;
            double totalSeconds = next == null ? 0 : seconds(a.time(), next.time());
            double delta = next == null ? 0 : next.energy() - a.energy();
            boolean validEnergy = next != null && delta >= 0 && Double.isFinite(delta) && totalSeconds > 0;
            for (LocalDateTime cursor = from; cursor.isBefore(to);) {
                LocalDateTime key = cursor.truncatedTo(ChronoUnit.HOURS);
                LocalDateTime limit = key.plusHours(1).isBefore(to) ? key.plusHours(1) : to;
                double seconds = seconds(cursor, limit);
                Hour h = hours.computeIfAbsent(key, Hour::new);
                if (air) {
                    h.observedSeconds += seconds;
                    if (a.opened()) {
                        h.runningSeconds += seconds;
                        h.temperatureSeconds += a.temperature() * seconds;
                        h.roomTemperatureSeconds += a.roomTemperature() * seconds;
                        h.modes.merge(a.mode() == null ? "UNKNOWN" : a.mode(), seconds, Double::sum);
                    }
                    if (a.errorCode() != 0) {
                        int last=h.faults.size()-1;
                        if(last>=0 && h.faults.get(last).errorCode()==a.errorCode() && h.faults.get(last).endAt().equals(cursor)) {
                            Fault previous=h.faults.get(last);
                            h.faults.set(last,new Fault(a.deviceId(),previous.startAt(),limit,a.errorCode()));
                        } else h.faults.add(new Fault(a.deviceId(),cursor,limit,a.errorCode()));
                    }
                } else {
                    if (validEnergy) {
                        h.energyKwh += delta * seconds / totalSeconds;
                        h.energyCoveredSeconds += seconds;
                        h.estimated |= !a.time().truncatedTo(ChronoUnit.HOURS).equals(next.time().minusNanos(1).truncatedTo(ChronoUnit.HOURS))
                                || totalSeconds > staleAfter.toSeconds();
                    }

                    if (!cursor.isBefore(a.time().plus(staleAfter))) h.estimated = true;
                }
                cursor = limit;
            }
        }
        if (!air) for (int i=1;i<samples.size();i++) {
            Sample sample=samples.get(i);
            if (sample.energy()<samples.get(i-1).energy() && !sample.time().isBefore(start) && sample.time().isBefore(end))
                hours.computeIfAbsent(sample.time().truncatedTo(ChronoUnit.HOURS), Hour::new).resets++;
        }
        // Peak is the largest actually sampled power, not an interpolated value.
        if (!air) for (Sample sample : samples) {
            if (!sample.time().isBefore(start) && sample.time().isBefore(end) && Double.isFinite(sample.power())) {
                hours.computeIfAbsent(sample.time().truncatedTo(ChronoUnit.HOURS), Hour::new).peakPowerW =
                    Math.max(hours.get(sample.time().truncatedTo(ChronoUnit.HOURS)).peakPowerW, sample.power());
            }
        }
        return List.copyOf(hours.values());
    }
    private static double seconds(LocalDateTime a, LocalDateTime b) { return Duration.between(a,b).toMillis() / 1000.0; }
}
