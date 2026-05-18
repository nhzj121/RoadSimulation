package org.example.roadsimulation.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.example.roadsimulation.entity.POI;

/**
 * POIType 枚举与数据库字符串之间的安全转换器。
 * 数据库中存在 poit_type 为空字符串的脏数据时，转换为 null 而非抛出 IllegalArgumentException。
 */
@Converter(autoApply = true)
public class POITypeConverter implements AttributeConverter<POI.POIType, String> {

    @Override
    public String convertToDatabaseColumn(POI.POIType attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public POI.POIType convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        if (dbData.isEmpty()) {
            return POI.POIType.WAREHOUSE;
        }
        try {
            return POI.POIType.valueOf(dbData);
        } catch (IllegalArgumentException e) {
            return POI.POIType.WAREHOUSE;
        }
    }
}
