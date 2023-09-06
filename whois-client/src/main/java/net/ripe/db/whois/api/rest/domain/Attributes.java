package net.ripe.db.whois.api.rest.domain;

import com.google.common.collect.Lists;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement(name = "attributes")
@XmlAccessorType(XmlAccessType.FIELD)
public class Attributes {

    @XmlElement(name = "attribute")
    private List<Attribute> attributes;

    public Attributes(final List<Attribute> attributes) {
        this.attributes = attributes;
    }

    public Attributes() {
        this.attributes = Lists.newArrayList();
    }

    public List<Attribute> getAttributes() {
        return this.attributes;
    }
}
