package com.beyond.generator.utils;

import com.beyond.gen.freemarker.JavaEntity;
import com.beyond.gen.freemarker.MapperEntity;
import com.beyond.gen.freemarker.MapperXmlEntity;
import com.beyond.generator.Column;
import com.beyond.generator.StringUtils;
import com.beyond.generator.TypeConverter;

import java.util.List;

/**
 * @author chenshipeng
 * @date 2022/11/23
 */
public class MapperUtil {
    public static MapperXmlEntity createMapperXmlEntity(JavaEntity javaEntity, MapperEntity mapperEntity, List<Column> columns){
        return createMapperXmlEntity(javaEntity.getPackageName() + "." + javaEntity.getClassName(), mapperEntity.getPackageName() + "." + mapperEntity.getMapperName(), columns);
    }

    public static MapperXmlEntity createMapperXmlEntity(String entityFullName, String mapperFullName, List<Column> columns){
        MapperXmlEntity mapperXmlEntity = new MapperXmlEntity();
        mapperXmlEntity.setEntityClassFullName(entityFullName);
        mapperXmlEntity.setMapperClassFullName(mapperFullName);
        for (Column column : columns) {
            String columnKey = column.getColumnKey();
            if (org.apache.commons.lang3.StringUtils.equalsIgnoreCase("PRI", columnKey)) {
                mapperXmlEntity.setIdColumnName(column.getColumnName());
                mapperXmlEntity.setIdPropertyName(StringUtils.lineToHump(column.getColumnName()));
                mapperXmlEntity.setIdJdbcType(TypeConverter.toCommonJdbcType(column.getDataType()).toUpperCase());
                break;
            }
        }
        for (Column column : columns) {
            String columnKey = column.getColumnKey();
            if (!org.apache.commons.lang3.StringUtils.equalsIgnoreCase("PRI", columnKey)) {
                MapperXmlEntity.ColumnEntity columnEntity = new MapperXmlEntity.ColumnEntity();
                columnEntity.setColumnName(column.getColumnName());
                columnEntity.setPropertyName(StringUtils.lineToHump(column.getColumnName()));
                columnEntity.setJdbcType(TypeConverter.toCommonJdbcType(column.getDataType()).toUpperCase());
                mapperXmlEntity.getNormalColumns().add(columnEntity);
            }
        }
        return mapperXmlEntity;
    }

    public static MapperXmlEntity createMapperXmlResultMapEntity(String entityFullName,List<Column> columns){
        return createMapperXmlEntity(entityFullName, null, columns);
    }

    public static MapperXmlEntity createMapperXmlColumnListEntity(List<Column> columns){
        return createMapperXmlEntity((String) null, (String)null, columns);
    }

}
