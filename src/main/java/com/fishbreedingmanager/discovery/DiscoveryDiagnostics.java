package com.fishbreedingmanager.discovery;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 为日志和后续诊断命令生成稳定、无第三方 Mod 特例的发现来源摘要。 */
public final class DiscoveryDiagnostics {
    private DiscoveryDiagnostics() {
    }

    /**
     * 输出至少包含一个 HIGH 或 MEDIUM 候选的来源。LOW 项仍保留在完整发现快照中，
     * 但不会被诊断误报为自动识别的可适配鱼类。
     *
     * @param snapshot 最近一次成功发布的发现快照
     * @return 按来源和实体 Registry ID 稳定排序的日志正文
     */
    public static List<String> sourceSummaries(DiscoverySnapshot snapshot) {
        return snapshot.detectedMods().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> summaryFor(entry.getKey(), entry.getValue(), snapshot))
                .filter(Objects::nonNull)
                .toList();
    }

    private static String summaryFor(String sourceModId, DetectedFishMod source,
            DiscoverySnapshot snapshot) {
        List<CandidateEntity> recognized = snapshot.candidates().values().stream()
                .filter(candidate -> candidate.sourceModId().equals(sourceModId))
                .filter(candidate -> candidate.confidence() != CandidateConfidence.LOW)
                .sorted(Comparator.comparing(candidate -> candidate.entityTypeId().toString()))
                .toList();
        if (recognized.isEmpty()) {
            return null;
        }

        long highCount = recognized.stream()
                .filter(candidate -> candidate.confidence() == CandidateConfidence.HIGH)
                .count();
        long mediumCount = recognized.size() - highCount;
        String candidateIds = recognized.stream()
                .map(candidate -> candidate.entityTypeId().toString())
                .collect(Collectors.joining(", ", "[", "]"));

        return "source=" + sourceModId
                + ", version=" + source.version()
                + ", registered=" + source.registeredEntityCount()
                + ", high=" + highCount
                + ", medium=" + mediumCount
                + ", candidateIds=" + candidateIds;
    }
}
