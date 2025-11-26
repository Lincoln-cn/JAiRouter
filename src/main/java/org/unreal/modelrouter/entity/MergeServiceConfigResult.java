package org.unreal.modelrouter.entity;

import java.util.List;
import java.util.Map;

public record MergeServiceConfigResult(Map<String, Object> mergedConfig, List<String> conflicts,
                                       List<String> warnings) {
}
