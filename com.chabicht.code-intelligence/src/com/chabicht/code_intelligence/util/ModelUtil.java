package com.chabicht.code_intelligence.util;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.chabicht.code_intelligence.Tuple;

public class ModelUtil {
	private ModelUtil() {
		// No instances.
	}

	public static Optional<Tuple<String, String>> getProviderModelTuple(String modelId) {
		Optional<Tuple<String, String>> res = Optional.empty();

		modelId = StringUtils.stripToEmpty(modelId);
		int firstSlashIndex = modelId.indexOf('/'); 
		if(firstSlashIndex>=0) {
			res = Optional.of(new Tuple<>(modelId.substring(0, firstSlashIndex), modelId.substring(firstSlashIndex+1)));
		}
		
		return res;
	}
}
