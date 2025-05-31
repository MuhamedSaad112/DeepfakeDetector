package com.deepfakedetector.mapper;

import com.deepfakedetector.model.dto.AdminUserDTO;
import com.deepfakedetector.model.entity.Authority;
import com.deepfakedetector.model.entity.User;
import org.mapstruct.*;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "authorities", target = "authorities")
    AdminUserDTO toDto(User user);

    @Mapping(target = "password", ignore = true)
    @Mapping(source = "authorities", target = "authorities")
    User toEntity(AdminUserDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateUserFromDto(AdminUserDTO dto, @MappingTarget User user);

    default Set<String> mapAuthoritiesToString(Set<Authority> authorities) {
        return authorities != null
                ? authorities.stream().map(Authority::getName).collect(Collectors.toSet())
                : null;
    }

    default Set<Authority> mapStringToAuthorities(Set<String> authorityNames) {
        return authorityNames != null
                ? authorityNames.stream().map(name -> {
            Authority auth = new Authority();
            auth.setName(name);
            return auth;
        }).collect(Collectors.toSet())
                : null;
    }
}
