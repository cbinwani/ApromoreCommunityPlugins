//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.7 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2019.01.14 at 11:30:43 AM AEDT 
//


package org.apromore.service.logvisualizer.ws.model;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the org.apromore.service.logvisualizer.ws.model package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _VisualizeLogRequest_QNAME = new QName("urn:qut-edu-au:schema:apromore:logvisualizer", "VisualizeLogRequest");
    private final static QName _VisualizeLogResponse_QNAME = new QName("urn:qut-edu-au:schema:apromore:logvisualizer", "VisualizeLogResponse");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: org.apromore.service.logvisualizer.ws.model
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link VisualizeLogInputMsgType }
     * 
     */
    public VisualizeLogInputMsgType createVisualizeLogInputMsgType() {
        return new VisualizeLogInputMsgType();
    }

    /**
     * Create an instance of {@link VisualizeLogOutputMsgType }
     * 
     */
    public VisualizeLogOutputMsgType createVisualizeLogOutputMsgType() {
        return new VisualizeLogOutputMsgType();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VisualizeLogInputMsgType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:qut-edu-au:schema:apromore:logvisualizer", name = "VisualizeLogRequest")
    public JAXBElement<VisualizeLogInputMsgType> createVisualizeLogRequest(VisualizeLogInputMsgType value) {
        return new JAXBElement<VisualizeLogInputMsgType>(_VisualizeLogRequest_QNAME, VisualizeLogInputMsgType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VisualizeLogOutputMsgType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:qut-edu-au:schema:apromore:logvisualizer", name = "VisualizeLogResponse")
    public JAXBElement<VisualizeLogOutputMsgType> createVisualizeLogResponse(VisualizeLogOutputMsgType value) {
        return new JAXBElement<VisualizeLogOutputMsgType>(_VisualizeLogResponse_QNAME, VisualizeLogOutputMsgType.class, null, value);
    }

}
