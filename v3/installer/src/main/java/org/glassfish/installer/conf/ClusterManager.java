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

import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.installer.util.GlassFishUtils;
import org.openinstaller.util.ClassUtils;
import org.openinstaller.util.ExecuteCommand;

/** Manages glassfish cluster related operations.
 * Operations such as start/stop/delete cluster are not exposed
 * through installer hence are not implemented in this class yet.
 * This utility class is invoked when the user chooses to create a
 * cluster as part of setting up a clustered glassfish instance.
 * Holds reference to Product object to get product wide information.
 *
 * @author sathyan
 */
public class ClusterManager {

    /* Reference to Product to obtain installation directory
     * and path to administration script.
     */
    private final Product productRef;
    /* Holds asadmin command output including the command line
     * of the recent runs. Gets overwritten on repeated calls to
     * asadmin commands. This text content will be used to construct
     * summary panel that displays the status/results of user configuration
     * actions.
     */
    private String outputFromRecentRun;
    /* Holds status of the configuration. Currently not used as createCluster
     * returns a valid Cluster object when the configuation is successful.
     * Can be used to double check in the calling code to make sure that
     * configuration was indeed successful/failure.
     */
    private boolean clusterConfigSuccessful;
    /* Logging */
    private static final Logger LOGGER;

    static {
        LOGGER = Logger.getLogger(ClassUtils.getClassName());
    }

    public ClusterManager(Product productRef) {
        this.productRef = productRef;
        outputFromRecentRun = null;
    }

    /* @return true/false, the value of the overall configuration status flag. */
    public boolean isClusterConfigSuccessful() {
        return clusterConfigSuccessful;
    }

    /* Creates the cluster by invoking asadmin's create-cluster command.
     * @param domainRef, reference to domain object. Used to check if the domain
     * is up or not. For a cluster to be created the domain should be running.
     * @param clusterName, name of the cluster to create.
     * @param clusterProperties, not currently used. refer to asadmin help create-cluster.
     * @param runningMode, "DRYRUN"/"REAL" "DRYRUN" mode would just return the
     * commandline and not execute it.
     * @returns a Cluster object, null if the cluster creation fails.
     */
    public Cluster createCluster(Domain domainRef, String clusterName, String clusterProperties, String runningMode) {

        DomainManager glassfishDomainManager = new DomainManager(productRef);


        clusterConfigSuccessful = true;

        if (!glassfishDomainManager.isDomainRunning(domainRef.getDomainName())) {
            outputFromRecentRun = "Domain " + domainRef.getDomainName() + " Not running\n";
            clusterConfigSuccessful = false;
            //TODO Start the domain..
            return null;
        }

        Cluster glassfishCluster = new Cluster(clusterName, clusterProperties);

        /* Create the actual command line for "asadmin create-cluster..." */
        ExecuteCommand asadminExecuteCommand =
                GlassFishUtils.assembleCreateClusterCommand(productRef, domainRef, glassfishCluster);

        outputFromRecentRun = "";

        if (asadminExecuteCommand != null) {
            LOGGER.log(Level.INFO, "Creating GlassFish cluster");
            LOGGER.log(Level.INFO, "with the following command line");

            /* Include the commandline also in the output. */
            outputFromRecentRun += asadminExecuteCommand.expandCommand(asadminExecuteCommand.getCommand()) + "\n";
            LOGGER.log(Level.INFO, outputFromRecentRun);

            if (runningMode.contains("DRYRUN")) {
                /*
                Do not execute the command, this is useful when the clients just
                wanted the actual commandline and not execute the command.*/
                return glassfishCluster;
            }
            try {
                asadminExecuteCommand.setOutputType(ExecuteCommand.ERRORS | ExecuteCommand.NORMAL);
                asadminExecuteCommand.setCollectOutput(true);
                asadminExecuteCommand.execute();
                outputFromRecentRun += asadminExecuteCommand.getAllOutput();
                LOGGER.log(Level.INFO, "Asadmin output: " + outputFromRecentRun);
                // Look for the string failed till asadmin bugs related to stderr are resolved.
                // Ugly/Buggy, but works for now.
                if (outputFromRecentRun.indexOf("failed") != -1) {
                    clusterConfigSuccessful = false;
                    glassfishCluster = null;
                }

            } catch (Exception e) {
                LOGGER.log(Level.INFO, "In exception, asadmin output: " + outputFromRecentRun);
                LOGGER.log(Level.INFO, "Exception while creating GlassFish Cluster: " + e.getMessage());
                glassfishCluster = null;
                clusterConfigSuccessful = false;
            }
        } else {
            outputFromRecentRun = "Command Line formation failed.";
            clusterConfigSuccessful = false;
            glassfishCluster = null;
        }
        return glassfishCluster;
    }

    /* No need for this functionality in 3.1, hence not implemented yet. */
    public boolean deleteCluster() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /* No need for this functionality in 3.1, hence not implemented yet. */
    public boolean stopCluster() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /* No need for this functionality in 3.1, hence not implemented yet. */
    public boolean startCluster() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /* No need for this functionality in 3.1, hence not implemented yet. */
    public boolean isClusterRunning() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /* Caller can get the output of recent asadmin command run. This has to be used
     * along with configSuccessful flag to find out the overall status of configuration.
     * @return String the whole of asadmin recent run's output including the command line.
     */
    public String getOutputFromRecentRun() {
        return this.outputFromRecentRun;
    }

    public boolean isConfigSuccessful() {
        return this.clusterConfigSuccessful;
    }
}
