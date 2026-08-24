package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.SiteLinkResponse;
import com.evernox.dto.SiteTagResponse;
import com.evernox.entity.SiteLink;
import com.evernox.entity.SiteLinkTag;
import com.evernox.entity.SiteTag;
import com.evernox.repository.SiteLinkTagRepository;
import com.evernox.repository.SiteTagRepository;
import com.evernox.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 站点响应装配器
 *
 * 用户侧与管理侧都要把标签、用户名批量补到响应里，抽出来避免两份实现走偏，
 * 同时保证是按页批量查询而非逐条查（N+1）。
 */
@Component
@RequiredArgsConstructor
public class SiteAssembler {

    private final SiteTagRepository siteTagRepository;
    private final SiteLinkTagRepository siteLinkTagRepository;
    private final UserRepository userRepository;

    /** 把一页站点实体装配为响应，批量补标签与用户名 */
    public IPage<SiteLinkResponse> convert(IPage<SiteLink> page) {
        List<SiteLink> records = page.getRecords();
        Map<Long, List<SiteTagResponse>> tagMap = loadTags(
                records.stream().map(site -> site.getId()).collect(Collectors.toList()));
        Map<Long, String> nameMap = loadUsernames(records);

        return page.convert(site -> {
            SiteLinkResponse resp = SiteLinkResponse.from(site);
            resp.setTags(tagMap.getOrDefault(site.getId(), List.of()));
            resp.setOwnerName(nameMap.get(site.getUserId()));
            resp.setReviewerName(site.getReviewedBy() == null ? null : nameMap.get(site.getReviewedBy()));
            return resp;
        });
    }

    /** 单条装配 */
    public SiteLinkResponse convert(SiteLink site) {
        SiteLinkResponse resp = SiteLinkResponse.from(site);
        resp.setTags(loadTags(List.of(site.getId())).getOrDefault(site.getId(), List.of()));
        Map<Long, String> nameMap = loadUsernames(List.of(site));
        resp.setOwnerName(nameMap.get(site.getUserId()));
        resp.setReviewerName(site.getReviewedBy() == null ? null : nameMap.get(site.getReviewedBy()));
        return resp;
    }

    /** 站点ID -> 标签列表 */
    @SuppressWarnings("null")
    public Map<Long, List<SiteTagResponse>> loadTags(Collection<Long> siteIds) {
        if (siteIds == null || siteIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<SiteLinkTag> relWrapper = new LambdaQueryWrapper<>();
        relWrapper.in(SiteLinkTag::getSiteId, siteIds);
        List<SiteLinkTag> relations = siteLinkTagRepository.selectList(relWrapper);
        if (relations.isEmpty()) {
            return Map.of();
        }

        Set<Long> tagIds = relations.stream().map(rel -> rel.getTagId()).collect(Collectors.toSet());
        LambdaQueryWrapper<SiteTag> tagWrapper = new LambdaQueryWrapper<>();
        tagWrapper.in(SiteTag::getId, tagIds)
                .orderByAsc(SiteTag::getSort)
                .orderByAsc(SiteTag::getId);
        Map<Long, SiteTagResponse> tags = siteTagRepository.selectList(tagWrapper).stream()
                .collect(Collectors.toMap(tag -> tag.getId(), tag -> SiteTagResponse.from(tag)));

        Map<Long, List<SiteTagResponse>> result = new HashMap<>();
        for (SiteLinkTag rel : relations) {
            SiteTagResponse tag = tags.get(rel.getTagId());
            if (tag != null) {
                result.computeIfAbsent(rel.getSiteId(), k -> new java.util.ArrayList<>()).add(tag);
            }
        }
        return result;
    }

    /** 用户ID -> 用户名，一次查完分享者与审批人 */
    private Map<Long, String> loadUsernames(Collection<SiteLink> sites) {
        Set<Long> userIds = new HashSet<>();
        for (SiteLink site : sites) {
            userIds.add(site.getUserId());
            if (site.getReviewedBy() != null) {
                userIds.add(site.getReviewedBy());
            }
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(user -> user.getId(), user -> user.getUsername()));
    }
}
