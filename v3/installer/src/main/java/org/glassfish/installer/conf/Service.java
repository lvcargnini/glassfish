/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Sun Microsystems, Inc. All rights reserved.
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
package org.glassfish.installer.conf;

/** Holds attributes of a GlassFish Service.
 *
 * @author sathyan
 */
public class Service {

    /* Name of the service. */
    private String serviceName;

    /* Properties(a colon separated list) currently used only in Solaris. */
    private String serviceProps;

    /* Currently this could either be a pointer to instance start/stop or domain
     * start/stop commands.
     */
    private String associatedExecutable;

    public Service(String serviceName, String serviceProps, String associatedExecutable) {
        this.serviceName = serviceName;
        this.serviceProps = serviceProps;
        this.associatedExecutable = associatedExecutable;
    }


    /* @return String find out the driving executable behind this service.
     * Complete path to the executable is returned.
     */
    public String getAssociatedExecutable() {
        return associatedExecutable;
    }

    /* @param associatedExecutable, path to the executable this service controls. */
    public void setAssociatedExecutable(String associatedExecutable) {
        this.associatedExecutable = associatedExecutable;
    }

    /* @return String name of the service. */
    public String getServiceName() {
        return serviceName;
    }

    /* @param serviceName, name of the service to create. */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    /* @return String service properties. */
    public String getServiceProps() {
        return serviceProps;
    }

    /* @param serviceProps, properties of the service to create. */
    public void setServiceProps(String serviceProps) {
        this.serviceProps = serviceProps;
    }
}
