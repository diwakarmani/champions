package com.propertyapp.mapper;

import com.propertyapp.dto.user.UserAddressDTO;
import com.propertyapp.dto.user.UserDTO;
import com.propertyapp.entity.user.User;
import com.propertyapp.entity.user.UserAddress;
import com.propertyapp.entity.user.Role;
import org.mapstruct.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {
    
    @Mapping(target = "roles", source = "roles", qualifiedByName = "rolesToStrings")
    @Mapping(target = "addresses", source = "addresses")
    UserDTO toDTO(User user);
    
    List<UserDTO> toDTOList(List<User> users);
    
    @Named("rolesToStrings")
    default Set<String> rolesToStrings(Set<Role> roles) {
        if (roles == null) {
            return null;
        }
        return roles.stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }
    
    @Mapping(target = "user", ignore = true)
    UserAddress toAddressEntity(UserAddressDTO dto);
    
    UserAddressDTO toAddressDTO(UserAddress entity);
    
    List<UserAddressDTO> toAddressDTOList(List<UserAddress> entities);
}