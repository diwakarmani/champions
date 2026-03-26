package com.propertyapp.entity.user;

import com.propertyapp.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends BaseEntity {
    
    @Column(name = "name", unique = true, nullable = false, length = 20)
    private String name;
    
    @Column(name = "description")
    private String description;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Role)) return false;
        Role role = (Role) o;
        return name != null && name.equals(role.getName());
    }
    
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}