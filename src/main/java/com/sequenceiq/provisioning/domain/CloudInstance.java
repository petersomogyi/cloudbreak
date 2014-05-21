package com.sequenceiq.provisioning.domain;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;

@Entity
@NamedQuery(
        name = "CloudInstance.findAllCloudForInfra",
        query = "SELECT c FROM CloudInstance c "
                + "WHERE c.infra.id= :id")
public class CloudInstance implements ProvisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cloudinstance_generator")
    @SequenceGenerator(name = "cloudinstance_generator", sequenceName = "cloudsequence_table")
    private Long id;

    private Integer clusterSize;

    private String name;

    @OneToOne
    private Infra infra;

    @ManyToOne
    private User user;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getClusterSize() {
        return clusterSize;
    }

    public void setClusterSize(Integer clusterSize) {
        this.clusterSize = clusterSize;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Infra getInfra() {
        return infra;
    }

    public void setInfra(Infra infra) {
        this.infra = infra;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

}
