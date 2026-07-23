package com.luohuo.flex.im.core.chat.consumer;

import com.luohuo.basic.cache.repository.CachePlusOps;
import com.luohuo.flex.common.cache.PassageMsgCacheKeyBuilder;
import com.luohuo.flex.common.constant.MqConstant;
import com.luohuo.flex.im.core.chat.dao.ContactDao;
import com.luohuo.flex.im.core.chat.dao.RoomFriendDao;
import com.luohuo.flex.im.core.chat.service.cache.GroupMemberCache;
import com.luohuo.flex.im.core.chat.service.cache.MsgCache;
import com.luohuo.flex.im.core.chat.service.cache.RoomCache;
import com.luohuo.flex.im.domain.entity.Message;
import com.luohuo.flex.im.domain.entity.Room;
import com.luohuo.flex.im.domain.entity.RoomFriend;
import com.luohuo.flex.im.domain.enums.RoomTypeEnum;
import com.luohuo.flex.model.ws.AckMessageDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Objects;

/**
 * 目前架构ws服务无法处理业务，客户端回执给ws服务之后进行mq跳转至此
 * @author 乾乾
 */
@Slf4j
@RocketMQMessageListener(consumerGroup = MqConstant.MSG_PUSH_ACK_TOPIC_GROUP, topic = MqConstant.MSG_PUSH_ACK_TOPIC, messageModel = MessageModel.CLUSTERING)
@Component
@AllArgsConstructor
public class AckConsumer implements RocketMQListener<AckMessageDTO> {

    private MsgCache msgCache;
    private ContactDao contactDao;
	private CachePlusOps cachePlusOps;
	private RoomCache roomCache;
	private GroupMemberCache groupMemberCache;
	private RoomFriendDao roomFriendDao;

	/**
	 * 通过mq的方式 回调进行回执
	 * @param dto
	 */
    @Override
    public void onMessage(AckMessageDTO dto) {
		cachePlusOps.sRem(PassageMsgCacheKeyBuilder.build(dto.getUid()), dto.getMsgId());

		Message message = msgCache.get(dto.getMsgId());
		if(message == null){
			return;
		}

		Room room = roomCache.get(message.getRoomId());
		if (room == null) {
			return;
		}

		if (!isMember(room, dto.getUid())) {
			return;
		}

		contactDao.refreshOrCreateActiveTime(message.getRoomId(), Arrays.asList(dto.getUid()), message.getId(), message.getCreateTime());
    }

	private boolean isMember(Room room, Long uid) {
		RoomTypeEnum roomType = RoomTypeEnum.of(room.getType());
		if (roomType == RoomTypeEnum.GROUP) {
			return groupMemberCache.getMemberDetail(room.getId(), uid) != null;
		}
		if (roomType == RoomTypeEnum.FRIEND) {
			RoomFriend roomFriend = roomFriendDao.getByRoomId(room.getId());
			return roomFriend != null && (Objects.equals(uid, roomFriend.getUid1()) || Objects.equals(uid, roomFriend.getUid2()));
		}
		return true;
	}
}
