// Path: src/main/java/com/pdts/model/RequirementType.java
package com.pdts.model;

import jakarta.persistence.*;

@Entity
@Table(name = "requirement_type")
public class RequirementType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "type_id")
    private Integer typeId;

    @Column(name = "requirement_type_name", nullable = false, length = 150)
    private String typeName;

    @Column(name = "type_is_active")
    private Integer isActive = 1;

    public Integer getTypeId() { return typeId; }
    public void setTypeId(Integer typeId) { this.typeId = typeId; }

    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
}
