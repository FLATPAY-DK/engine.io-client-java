package io.socket.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PropertiesInspector {
	public static Map<String, Object> getProperties(Object obj) {
		if(obj == null) return null;
		List<Field> fields = new ArrayList<>();
		for (var field : obj.getClass().getDeclaredFields()) {
			try {
				field.setAccessible(true);
				fields.add(field);
			} catch (Exception e) {
				/*Skip the element*/
			}
		}
		Map<String, Object> map = new HashMap<>();
		for (var field : fields) {
			try {
				map.put(
						field.getName(),
						field.get(obj)
				);
			} catch (IllegalAccessException e) {
				map.put(field.getName(), null);
			}
		}
		return map;
	}
}
