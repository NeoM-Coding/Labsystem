package xyz.jasenon.lab.engine.eval;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.common.model.device.records.AirConditionRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypedValueParserTests {

    @Test
    void resolvesRecordFieldType() {
        assertEquals(boolean.class, RecordFieldTypeResolver.resolve(DeviceType.AirCondition, "opened"));
        assertEquals(int.class, RecordFieldTypeResolver.resolve(DeviceType.AirCondition, "roomTemperature"));
        assertEquals(AirConditionRecord.Mode.class, RecordFieldTypeResolver.resolve(DeviceType.AirCondition, "mode"));
    }

    @Test
    void parsesAndComparesTypedValues() {
        assertEquals(true, TypedValueParser.parse(DeviceType.AirCondition, "opened", "true"));
        assertTrue(TypedValueParser.compare(DeviceType.AirCondition, "roomTemperature", Operator.GT, "27", "26"));
        assertTrue(TypedValueParser.compare(DeviceType.CircuitBreak, "voltage", Operator.GE, "220.5", "220.50"));
        assertTrue(TypedValueParser.compare(DeviceType.AirCondition, "mode", Operator.EQ, "Cooling", "Cooling"));
        assertFalse(TypedValueParser.compare(DeviceType.AirCondition, "mode", Operator.GT, "Cooling", "Heating"));
    }

    @Test
    void rejectsUnknownOrInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> RecordFieldTypeResolver.resolve(DeviceType.Sensor, "missing"));
        assertThrows(IllegalArgumentException.class, () -> TypedValueParser.parse(DeviceType.Sensor, "temperature", "bad-number"));
        assertThrows(IllegalArgumentException.class, () -> TypedValueParser.parse(DeviceType.AirCondition, "speed", "VeryFast"));
    }
}
