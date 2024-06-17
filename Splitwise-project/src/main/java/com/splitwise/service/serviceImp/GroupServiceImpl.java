package com.splitwise.service.serviceImp;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.splitwise.dto.GroupDto;
import com.splitwise.entity.Group;
import com.splitwise.entity.User;
import com.splitwise.repository.GroupRepository;
import com.splitwise.service.GroupService;
import com.splitwise.service.UserService;

@Service
@@AllArgsConstructor
public class GroupServiceImpl implements GroupService {

	private GroupRepository groupRepository;

	private UserService userService;

	public GroupDto createGroup(GroupDto groupDTO) {
		try {
			Group group = new Group();
			group.setGroupName(groupDTO.getGroupName());
			group.setCurrency(groupDTO.getCurrency());

			Set<User> members = new HashSet<>();
			for (Long memberId : groupDTO.getMemberIds()) {
				members.add(userService.getUserEntityById(memberId));
			}
			group.setMembers(members);

			Group savedGroup = groupRepository.save(group);
			return toDto(savedGroup);
		} catch (Exception e) {
			throw new RuntimeException("Error creating group", e);
		}
	}

	private GroupDto toDto(Group group) {
		GroupDto groupDTO = new GroupDto();
		groupDTO.setGroupId(group.getGroupId());
		groupDTO.setGroupName(group.getGroupName());
		groupDTO.setCurrency(group.getCurrency());
		groupDTO.setMemberIds(group.getMembers().stream().map(User::getUserId).collect(Collectors.toSet()));
		return groupDTO;
	}
}
