/**
 * Copyright (c) 2000-2012 Liferay, Inc. All rights reserved.
 *
 * This file is part of Liferay Social Office. Liferay Social Office is free
 * software: you can redistribute it and/or modify it under the terms of the GNU
 * Affero General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * Liferay Social Office is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Liferay Social Office. If not, see http://www.gnu.org/licenses/agpl-3.0.html.
 */

package com.liferay.so.service.impl;

import com.liferay.mail.service.MailServiceUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.mail.MailMessage;
import com.liferay.portal.kernel.notifications.ChannelHubManagerUtil;
import com.liferay.portal.kernel.notifications.NotificationEvent;
import com.liferay.portal.kernel.notifications.NotificationEventFactoryUtil;
import com.liferay.portal.kernel.util.HttpUtil;
import com.liferay.portal.kernel.util.PrefsPropsUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.uuid.PortalUUIDUtil;
import com.liferay.portal.model.Group;
import com.liferay.portal.model.User;
import com.liferay.portal.service.ServiceContext;
import com.liferay.so.MemberRequestAlreadyUsedException;
import com.liferay.so.MemberRequestInvalidUserException;
import com.liferay.so.invitemembers.util.InviteMembersConstants;
import com.liferay.so.model.MemberRequest;
import com.liferay.so.service.base.MemberRequestLocalServiceBaseImpl;
import com.liferay.so.util.PortletKeys;

import java.util.Date;
import java.util.List;

import javax.mail.internet.InternetAddress;

/**
 * @author Ryan Park
 * @author Jonathan Lee
 */
public class MemberRequestLocalServiceImpl
	extends MemberRequestLocalServiceBaseImpl {

	public MemberRequest addMemberRequest(
			long userId, long groupId, long receiverUserId,
			String receiverEmailAddress, long invitedRoleId, long invitedTeamId,
			ServiceContext serviceContext)
		throws PortalException, SystemException {

		// Member request

		User user = userLocalService.getUserById(userId);

		Date now = new Date();

		long memberRequestId = counterLocalService.increment();

		MemberRequest memberRequest = memberRequestPersistence.create(
			memberRequestId);

		memberRequest.setGroupId(groupId);
		memberRequest.setCompanyId(user.getCompanyId());
		memberRequest.setUserId(userId);
		memberRequest.setUserName(user.getFullName());
		memberRequest.setCreateDate(now);
		memberRequest.setModifiedDate(now);
		memberRequest.setKey(PortalUUIDUtil.generate());
		memberRequest.setReceiverUserId(receiverUserId);
		memberRequest.setInvitedRoleId(invitedRoleId);
		memberRequest.setInvitedTeamId(invitedTeamId);
		memberRequest.setStatus(InviteMembersConstants.STATUS_PENDING);

		memberRequestPersistence.update(memberRequest);

		// Email

		try {
			sendEmail(receiverEmailAddress, memberRequest, serviceContext);
		}
		catch (Exception e) {
			throw new SystemException(e);
		}

		// Notifications

		sendNotificationEvent(memberRequest);

		return memberRequest;
	}

	public void addMemberRequests(
			long userId, long groupId, long[] receiverUserIds,
			long invitedRoleId, long invitedTeamId,
			ServiceContext serviceContext)
		throws PortalException, SystemException {

		for (long receiverUserId : receiverUserIds) {
			if (hasPendingMemberRequest(groupId, receiverUserId)) {
				continue;
			}

			User user = userLocalService.getUser(receiverUserId);

			String emailAddress = user.getEmailAddress();

			addMemberRequest(
				userId, groupId, receiverUserId, emailAddress, invitedRoleId,
				invitedTeamId, serviceContext);
		}
	}

	public void addMemberRequests(
			long userId, long groupId, String[] emailAddresses,
			long invitedRoleId, long invitedTeamId,
			ServiceContext serviceContext)
		throws PortalException, SystemException {

		for (String emailAddress : emailAddresses) {
			if (!Validator.isEmailAddress(emailAddress)) {
				continue;
			}

			addMemberRequest(
				userId, groupId, 0, emailAddress, invitedRoleId, invitedTeamId,
				serviceContext);
		}
	}

	public MemberRequest getMemberRequest(
			long groupId, long receiverUserId, int status)
		throws PortalException, SystemException {

		return memberRequestPersistence.findByG_R_S(
			groupId, receiverUserId, status);
	}

	public List<MemberRequest> getReceiverMemberRequest(
			long receiverUserId, int start, int end)
		throws SystemException {

		return memberRequestPersistence.findByReceiverUserId(receiverUserId);
	}

	public int getReceiverMemberRequestCount(long receiverUserId)
		throws SystemException {

		return memberRequestPersistence.countByReceiverUserId(receiverUserId);
	}

	public List<MemberRequest> getReceiverStatusMemberRequest(
			long receiverUserId, int status, int start, int end)
		throws SystemException {

		return memberRequestPersistence.findByR_S(
			receiverUserId, status, start, end);
	}

	public int getReceiverStatusMemberRequestCount(
			long receiverUserId, int status)
		throws SystemException {

		return memberRequestPersistence.countByR_S(receiverUserId, status);
	}

	public boolean hasPendingMemberRequest(long groupId, long receiverUserId)
		throws SystemException {

		MemberRequest memberRequest = memberRequestPersistence.fetchByG_R_S(
			groupId, receiverUserId, InviteMembersConstants.STATUS_PENDING);

		if (memberRequest != null) {
			return true;
		}
		else {
			return false;
		}
	}

	public MemberRequest updateMemberRequest(
			long userId, long memberRequestId, int status)
		throws Exception {

		MemberRequest memberRequest = memberRequestPersistence.findByPrimaryKey(
			memberRequestId);

		validate(memberRequest, userId);

		memberRequest.setModifiedDate(new Date());
		memberRequest.setStatus(status);

		memberRequestPersistence.update(memberRequest);

		if (status == InviteMembersConstants.STATUS_ACCEPTED) {
			userLocalService.addGroupUsers(
				memberRequest.getGroupId(),
				new long[] {memberRequest.getReceiverUserId()});

			if (memberRequest.getInvitedRoleId() > 0) {
				userGroupRoleLocalService.addUserGroupRoles(
					new long[] {memberRequest.getReceiverUserId()},
					memberRequest.getGroupId(),
					memberRequest.getInvitedRoleId());
			}

			if (memberRequest.getInvitedTeamId() > 0) {
				userLocalService.addTeamUsers(
					memberRequest.getInvitedTeamId(),
					new long[] {memberRequest.getReceiverUserId()});
			}
		}

		return memberRequest;
	}

	public MemberRequest updateMemberRequest(String key, long receiverUserId)
		throws PortalException, SystemException {

		MemberRequest memberRequest = memberRequestPersistence.findByKey(key);

		validate(memberRequest, 0);

		memberRequest.setModifiedDate(new Date());
		memberRequest.setReceiverUserId(receiverUserId);

		memberRequestPersistence.update(memberRequest);

		return memberRequest;
	}

	protected void sendEmail(
			String emailAddress, MemberRequest memberRequest,
			ServiceContext serviceContext)
		throws Exception {

		long companyId = memberRequest.getCompanyId();

		Group group = groupLocalService.getGroup(memberRequest.getGroupId());

		User user = userLocalService.getUser(memberRequest.getUserId());

		User receiverUser = null;

		if (memberRequest.getReceiverUserId() > 0) {
			receiverUser = userLocalService.getUser(
				memberRequest.getReceiverUserId());
		}

		String fromName = PrefsPropsUtil.getString(
			companyId, PropsKeys.ADMIN_EMAIL_FROM_NAME);
		String fromAddress = PrefsPropsUtil.getString(
			companyId, PropsKeys.ADMIN_EMAIL_FROM_ADDRESS);

		String toName = StringPool.BLANK;
		String toAddress = emailAddress;

		if (receiverUser != null) {
			toName = receiverUser.getFullName();
		}

		String subject = StringUtil.read(
			getClassLoader(),
			"com/liferay/so/invitemembers/dependencies/subject.tmpl");

		String body = StringPool.BLANK;

		if (memberRequest.getReceiverUserId() > 0) {
			body = StringUtil.read(
				getClassLoader(),
				"com/liferay/so/invitemembers/dependencies/" +
					"existing_user_body.tmpl");
		}
		else {
			body = StringUtil.read(
				getClassLoader(),
				"com/liferay/so/invitemembers/dependencies/" +
					"new_user_body.tmpl");
		}

		subject = StringUtil.replace(
			subject,
			new String[] {
				"[$MEMBER_REQUEST_GROUP$]", "[$MEMBER_REQUEST_USER$]"
			},
			new String[] {
				group.getDescriptiveName(serviceContext.getLocale()),
				user.getFullName()
			});

		String createAccountURL = (String)serviceContext.getAttribute(
			"createAccountURL");

		if (Validator.isNull(createAccountURL)) {
			createAccountURL = serviceContext.getPortalURL();
		}

		createAccountURL = HttpUtil.addParameter(
			createAccountURL, "key", memberRequest.getKey());

		String loginURL = (String)serviceContext.getAttribute("loginURL");

		if (Validator.isNull(loginURL)) {
			loginURL = serviceContext.getPortalURL();
		}

		loginURL = HttpUtil.addParameter(
			loginURL, "key", memberRequest.getKey());

		body = StringUtil.replace(
			body,
			new String[] {
				"[$ADMIN_ADDRESS$]", "[$ADMIN_NAME$]",
				"[$MEMBER_REQUEST_CREATE_ACCOUNT_URL$]",
				"[$MEMBER_REQUEST_GROUP$]", "[$MEMBER_REQUEST_LOGIN_URL$]",
				"[$MEMBER_REQUEST_USER$]"
			},
			new String[] {
				fromAddress, fromName, createAccountURL,
				group.getDescriptiveName(serviceContext.getLocale()), loginURL,
				user.getFullName()
			});

		InternetAddress from = new InternetAddress(fromAddress, fromName);

		InternetAddress to = new InternetAddress(toAddress, toName);

		MailMessage mailMessage = new MailMessage(
			from, to, subject, body, true);

		MailServiceUtil.sendEmail(mailMessage);
	}

	protected void sendNotificationEvent(MemberRequest memberRequest)
		throws PortalException {

		JSONObject notificationEventJSONObject =
			JSONFactoryUtil.createJSONObject();

		notificationEventJSONObject.put("groupId", memberRequest.getGroupId());
		notificationEventJSONObject.put(
			"memberRequestId", memberRequest.getMemberRequestId());
		notificationEventJSONObject.put(
			"portletId", PortletKeys.SO_INVITE_MEMBERS);
		notificationEventJSONObject.put("title", "x-invited-you-to-join-x");
		notificationEventJSONObject.put("userId", memberRequest.getUserId());

		NotificationEvent notificationEvent =
			NotificationEventFactoryUtil.createNotificationEvent(
				System.currentTimeMillis(), PortletKeys.SO_NOTIFICATION,
				notificationEventJSONObject);

		notificationEvent.setDeliveryRequired(0);

		ChannelHubManagerUtil.sendNotificationEvent(
			memberRequest.getCompanyId(), memberRequest.getReceiverUserId(),
			notificationEvent);
	}

	protected void validate(MemberRequest memberRequest, long userId)
		throws PortalException {

		if (memberRequest.getStatus() !=
				InviteMembersConstants.STATUS_PENDING) {

			throw new MemberRequestAlreadyUsedException();
		}
		else if (memberRequest.getReceiverUserId() != userId) {
			throw new MemberRequestInvalidUserException();
		}
	}

}