package com.luohuo.flex.im.core.user.service;

import com.luohuo.flex.im.domain.vo.req.feed.FeedPageReq;
import com.luohuo.flex.im.domain.vo.res.CursorPageBaseResp;
import com.luohuo.flex.im.domain.vo.req.feed.FeedParam;
import com.luohuo.flex.im.domain.vo.req.feed.FeedPermission;
import com.luohuo.flex.im.domain.vo.req.feed.FeedVo;
import org.springframework.transaction.annotation.Transactional;

/**
 * 朋友圈核心服务
 */
public interface FeedService {

	CursorPageBaseResp<FeedVo> getFeedPage(FeedPageReq request, Long uid);

	Boolean pushFeed(Long uid, FeedParam param);

	@Transactional
	Boolean delFeed(Long feedId);

	FeedVo getDetail(Long id);

	FeedVo feedDetail(Long feedId, Long uid);

	/**
	 * 获取指定用户最新一条可见的朋友圈
	 * @param targetUid 被查看的用户ID
	 * @param requesterUid 当前查看人ID
	 */
	FeedVo getLatestByUser(Long targetUid, Long requesterUid);

	FeedPermission getFeedPermission(Long uid, Long feedId);

	Boolean editFeed(Long uid, FeedParam param);
}
