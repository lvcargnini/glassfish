/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.webservices;

import com.sun.enterprise.deployment.WebServiceEndpoint;
import com.sun.enterprise.deployment.EjbDescriptor;
import com.sun.enterprise.deployment.ResourceReferenceDescriptor;
import com.sun.enterprise.deployment.InjectionTarget;
import com.sun.logging.LogDomains;
import org.glassfish.ejb.api.EjbEndpointFacade;

import com.sun.xml.ws.transport.http.servlet.ServletAdapterList;
import com.sun.xml.ws.transport.http.servlet.ServletAdapter;
import com.sun.xml.ws.api.server.SDDocumentSource;
import com.sun.xml.ws.api.server.Invoker;
import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.BindingID;

import javax.xml.ws.WebServiceContext;
import javax.xml.ws.soap.MTOMFeature;
import java.util.ResourceBundle;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.File;

import org.glassfish.api.invocation.ComponentInvocation;
import org.glassfish.api.invocation.InvocationManager;
import org.glassfish.api.admin.ServerEnvironment;


/**
 * Runtime dispatch information about one ejb web service
 * endpoint.  This class must support concurrent access,
 * since a single instance will be used for all web
 * service invocations through the same ejb endpoint.
 * <p><b>NOT THREAD SAFE</b>
 * @author Jerome Dochez
 */
public class EjbRuntimeEndpointInfo {

    protected Logger logger = LogDomains.getLogger(this.getClass(), LogDomains.WEBSERVICES_LOGGER);

    private ResourceBundle rb = logger.getResourceBundle()   ;

    protected final WebServiceEndpoint endpoint;

    protected final EjbEndpointFacade container;

    protected final Object webServiceEndpointServant;

    // the variables below are access in non-thread-safe ways
    private ServletAdapter adapter = null;
    private ServletAdapterList adapterList = null;

    private WebServiceContextImpl wsCtxt = null;
    private boolean handlersConfigured = false;

    protected EjbMessageDispatcher messageDispatcher = null;

    public EjbRuntimeEndpointInfo(WebServiceEndpoint webServiceEndpoint,
                                  EjbEndpointFacade ejbContainer,
                                  Object servant) {

        endpoint = webServiceEndpoint;
        container  = ejbContainer;
        webServiceEndpointServant = servant;


    }



    public WebServiceEndpoint getEndpoint() {
        return endpoint;
    }

    public String getEndpointAddressUri() {
        return endpoint.getEndpointAddressUri();
    }

    public WebServiceContext getWebServiceContext() {
        return wsCtxt;
    }

    public Object prepareInvocation(boolean doPreInvoke)
        throws Exception {
        ComponentInvocation inv;
        AdapterInvocationInfo adapterInvInfo = new AdapterInvocationInfo();
        // For proper injection of handlers, we have to configure handler
        // after invManager.preInvoke but the Invocation.contextData has to be set
        // before invManager.preInvoke. So the steps of configuring jaxws handlers and
        // init'ing jaxws is done here - this sequence is important
        if (adapter==null) {
            synchronized(this) {
                if(adapter == null) {
                    try {
                        // Set webservice context here
                        // If the endpoint has a WebServiceContext with @Resource then
                        // that has to be used


                        EjbDescriptor ejbDesc = endpoint.getEjbComponentImpl();
                        Iterator<ResourceReferenceDescriptor> it = ejbDesc.getResourceReferenceDescriptors().iterator();
                        while(it.hasNext()) {
                            ResourceReferenceDescriptor r = it.next();
                            if(r.isWebServiceContext()) {
                                Iterator<InjectionTarget> iter = r.getInjectionTargets().iterator();
                                boolean matchingClassFound = false;
                                while(iter.hasNext()) {
                                    InjectionTarget target = iter.next();
                                    if(ejbDesc.getEjbClassName().equals(target.getClassName())) {
                                        matchingClassFound = true;
                                        break;
                                    }
                                }
                                if(!matchingClassFound) {
                                    continue;
                                }
                                try {
                                    javax.naming.InitialContext ic = new javax.naming.InitialContext();
                                    wsCtxt = (WebServiceContextImpl) ic.lookup("java:comp/env/" + r.getName());
                                } catch (Throwable t) {
                                    logger.fine("Error In EjbRuntimeEndpointInfo" + t.getCause());
                                }
                            }
                        }
                        if(wsCtxt == null) {
                            wsCtxt = new WebServiceContextImpl();
                        }
                    } catch (Throwable t) {
                        logger.severe("Cannot initialize endpoint " + endpoint.getName() + " : error is : " + t.getMessage());
                        return null;
                    } finally {
                //TODOBM fixme        invManager.postInvoke(invManager.getCurrentInvocation());
                    }
                }
            }
        }

        if(doPreInvoke) {
            inv =  container.startInvocation();
            adapterInvInfo.setInv(inv);

        }

        // Now process handlers and init jaxws RI
        if(!handlersConfigured && doPreInvoke) {
            synchronized(this) {
                if(!handlersConfigured) {
                    try {
                        WsUtil wsu = new WsUtil();
                        String implClassName = endpoint.getEjbComponentImpl().getEjbClassName();
                        Class clazz = container.getEndpointClassLoader().loadClass(implClassName);

                        // Get the proper binding using BindingID
                        String givenBinding = endpoint.getProtocolBinding();

                        // Get list of all wsdls and schema
                        SDDocumentSource primaryWsdl = null;
                        Collection docs = null;
                        if(endpoint.getWebService().hasWsdlFile()) {

                            //TODO BM handle this later
                            /*BaseManager mgr;
                            if(endpoint.getBundleDescriptor().getApplication().isVirtual()) {
                                mgr = DeploymentServiceUtils.getInstanceManager(DeployableObjectType.EJB);
                            } else {
                                mgr = DeploymentServiceUtils.getInstanceManager(DeployableObjectType.APP);
                            }
                            String deployedDir =
                                mgr.getLocation(endpoint.getBundleDescriptor().getApplication().getRegistrationName());
                                */
                            WebServiceContractImpl wscImpl = WebServiceContractImpl.getInstance();
                            ServerEnvironment servEnv = wscImpl.getServerEnvironmentImpl();
                            String deployedDir = new File(servEnv.getApplicationRepositoryPath().getAbsolutePath(),
                                               endpoint.getBundleDescriptor().getApplication().getRegistrationName()).getAbsolutePath();
                                        

                            File pkgedWsdl = null;
                            if(deployedDir != null) {
                                if(endpoint.getBundleDescriptor().getApplication().isVirtual()) {
                                    pkgedWsdl = new File(deployedDir+File.separator+
                                                endpoint.getWebService().getWsdlFileUri());
                                } else {
                                    pkgedWsdl = new File(deployedDir+File.separator+
                                            endpoint.getBundleDescriptor().getModuleDescriptor().getArchiveUri().replaceAll("\\.", "_") +
                                            File.separator + endpoint.getWebService().getWsdlFileUri());
                                }
                            } else {
                                pkgedWsdl = new File(endpoint.getWebService().getWsdlFileUrl().getFile());
                            }
                            if(pkgedWsdl.exists()) {
                                primaryWsdl = SDDocumentSource.create(pkgedWsdl.toURL());
                                docs = wsu.getWsdlsAndSchemas(pkgedWsdl);
                            }
                        }

                        // Create a Container to pass ServletContext and also inserting the pipe
                        JAXWSContainer container = new JAXWSContainer(null,
                                endpoint);

                        // Get catalog info
                        java.net.URL catalogURL = null;
                        File catalogFile = new File(endpoint.getBundleDescriptor().getDeploymentDescriptorDir() +
                                File.separator + "jax-ws-catalog.xml");
                        if(catalogFile.exists()) {
                            catalogURL = catalogFile.toURL();
                        }

                        // Create Binding and set service side handlers on this binding

                        boolean mtomEnabled = wsu.getMtom(endpoint);
                        WSBinding binding = null;
                        // Only if MTOm is enabled create the Binding with the MTOMFeature
                        if (mtomEnabled) {
                            MTOMFeature mtom = new MTOMFeature(true);
                            binding = BindingID.parse(givenBinding).createBinding(mtom);
                        } else {
                            binding = BindingID.parse(givenBinding).createBinding();
                        }
                        wsu.configureJAXWSServiceHandlers(endpoint,
                            endpoint.getProtocolBinding(), binding);

                        // Create the jaxws2.1 invoker and use this
                        Invoker invoker = new InstanceResolverImpl(clazz).createInvoker();
                        WSEndpoint wsep = WSEndpoint.create(
                                clazz, // The endpoint class
                                false, // we do not want JAXWS to process @HandlerChain
                                new EjbInvokerImpl(clazz, invoker, webServiceEndpointServant, wsCtxt), // the invoker
                                endpoint.getServiceName(), // the service QName
                                endpoint.getWsdlPort(), // the port
                                container,
                                binding, // Derive binding
                                primaryWsdl, // primary WSDL
                                docs, // Collection of imported WSDLs and schema
                                catalogURL
                                );

                        String uri = endpoint.getEndpointAddressUri();
                        String urlPattern = uri.startsWith("/") ? uri : "/" + uri;
                        /*if(urlPattern.indexOf("/", 1) != -1) {
                            urlPattern = urlPattern.substring(urlPattern.indexOf("/", 1));
                        }
*/
                        // All set; Create the adapter
                        if(adapterList == null) {
                            adapterList = new ServletAdapterList();
                        }
                        adapter = adapterList.createAdapter(endpoint.getName(), urlPattern, wsep);
                        handlersConfigured=true;
                    } catch (Throwable t) {
                        logger.severe("Cannot initialize endpoint " + endpoint.getName() + " : error is : " + t.getMessage());
                        t.printStackTrace();
                        adapter = null;
                    }
                }
            }
        }
        adapterInvInfo.setAdapter(adapter);
        return adapterInvInfo;
    }

   /**
     * Force initialization of the endpoint runtime information  
     * as well as the handlers injection 
     */
    public void initRuntimeInfo(ServletAdapterList list) throws Exception {
       AdapterInvocationInfo aInfo =null;
        try { 
            this.adapterList = list;
            aInfo = (AdapterInvocationInfo)prepareInvocation(true);
        } finally {
            releaseImplementor(aInfo.getInv())       ;
        } 
         
    } 


    public InvocationManager getInvocationManager (){
        WebServiceContractImpl wscImpl = WebServiceContractImpl.getInstance();
        return wscImpl.getInvocationManager();
    }

    /**
     * Called after attempt to handle message.  This is coded defensively
     * so we attempt to clean up no matter how much progress we made in
     * getImplementor.  One important thing is to complete the invocation
     * manager preInvoke().
     */
    public void releaseImplementor(ComponentInvocation inv) {

        container.endInvocation(inv);

    }
    
    public EjbMessageDispatcher getMessageDispatcher() {
        if (messageDispatcher==null) {
            messageDispatcher = new Ejb3MessageDispatcher();            
        }
        return messageDispatcher;
    }

    public EjbEndpointFacade getContainer() {
        return container;
    }


   
}
