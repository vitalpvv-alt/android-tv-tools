package ru.vital.tv.sunset;

import org.junit.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Objects;
import static org.junit.Assert.*;

public final class SolarOracleTest {
    @Test public void agreesWithIndependentAstralForFourteenLocationsAcrossAllMonths() throws Exception {
        int count=0;
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/solar-oracle.csv")),StandardCharsets.UTF_8))) {
            String line;
            while((line=reader.readLine())!=null) {
                if(line.startsWith("#")||line.startsWith("city|"))continue;
                String[] v=line.split("\\|");LocalDate date=LocalDate.parse(v[4]);
                Plan plan=new Plan(v[0],Double.parseDouble(v[1]),Double.parseDouble(v[2]),v[3],100,0);
                long noon=date.atTime(12,0).atZone(plan.zone).toInstant().toEpochMilli();
                Solar.Day actual=plan.at(noon).today;
                assertNotNull(line,actual.sunrise);assertNotNull(line,actual.sunset);
                assertEquals(v[0]+" sunrise "+date,Double.parseDouble(v[5]),actual.sunrise.toEpochMilli(),120_000);
                assertEquals(v[0]+" sunset "+date,Double.parseDouble(v[6]),actual.sunset.toEpochMilli(),120_000);
                assertEquals(date,actual.sunrise.atZone(plan.zone).toLocalDate());
                assertEquals(date,actual.sunset.atZone(plan.zone).toLocalDate());
                count++;
            }
        }
        assertEquals(168,count);
    }
}
