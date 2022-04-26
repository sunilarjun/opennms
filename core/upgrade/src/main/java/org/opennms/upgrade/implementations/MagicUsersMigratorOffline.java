/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2014-2022 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2022 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.upgrade.implementations;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.opennms.core.utils.BundleLists;
import org.opennms.core.utils.ConfigFileConstants;
import org.opennms.netmgt.config.UserFactory;
import org.opennms.netmgt.config.UserManager;
import org.opennms.netmgt.model.OnmsUser;
import org.opennms.upgrade.api.AbstractOnmsUpgrade;
import org.opennms.upgrade.api.OnmsUpgradeException;
import org.opennms.web.api.Authentication;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Migrate the content from magic-users.properties into the users.xml file
 * 
 * <p>Issues fixed:</p>
 * <ul>
 * <li>HZN-871</li>
 * </ul>
 * 
 * @author <a href="mailto:agalue@opennms.org">Alejandro Galue</a> 
 */
public class MagicUsersMigratorOffline extends AbstractOnmsUpgrade {

    /** The magic users file. */
    private File magicUsersFile;
    private File magicUsersFileRPM;
    private File magicUsersFileDEB;

    /** The users file. */
    private File usersFile;

    /**
     * Instantiates a new magic users migrator offline.
     *
     * @throws OnmsUpgradeException the OpenNMS upgrade exception
     */
    public MagicUsersMigratorOffline() throws OnmsUpgradeException {
        super();
        try {
            magicUsersFile = new File(ConfigFileConstants.getHome(), "etc" + File.separator + "magic-users.properties");
            magicUsersFileRPM = new File(magicUsersFile.getAbsolutePath() + ".rpmsave");
            magicUsersFileDEB = new File(magicUsersFile.getAbsolutePath() + ".dpkg-remove");
            usersFile = ConfigFileConstants.getFile(ConfigFileConstants.USERS_CONF_FILE_NAME);
        } catch (Exception e) {
        }
    }

    /* (non-Javadoc)
     * @see org.opennms.upgrade.api.OnmsUpgrade#getOrder()
     */
    @Override
    public int getOrder() {
        return 12;
    }

    /* (non-Javadoc)
     * @see org.opennms.upgrade.api.OnmsUpgrade#getDescription()
     */
    @Override
    public String getDescription() {
        return "Moves security roles from magic-users.properties into the users.xml file: HZN-871";
    }

    /* (non-Javadoc)
     * @see org.opennms.upgrade.api.OnmsUpgrade#requiresOnmsRunning()
     */
    @Override
    public boolean requiresOnmsRunning() {
        return false;
    }

    /* (non-Javadoc)
     * @see org.opennms.upgrade.api.OnmsUpgrade#preExecute()
     */
    @Override
    public void preExecute() throws OnmsUpgradeException {
        if (!canRun()) return;

        try {
            File[] files = { magicUsersFile, magicUsersFileRPM, magicUsersFileDEB, usersFile };
            for (File file : files) {
                if (file.exists()) {
                    log("Backing up %s\n", file);
                    zipFile(file);
                }
            }
        } catch (Exception e) {
            throw new OnmsUpgradeException("Can't backup files because " + e.getMessage());
        }
    }

    /* (non-Javadoc)
     * @see org.opennms.upgrade.api.OnmsUpgrade#postExecute()
     */
    @Override
    public void postExecute() throws OnmsUpgradeException {
        if (!canRun()) return;

        // Delete the original configuration file so that it doesn't get re-migrated later
        if (magicUsersFile.exists()) {
            log("Removing original config file %s\n", magicUsersFile);
            FileUtils.deleteQuietly(magicUsersFile);
        }
        if (magicUsersFileRPM.exists()) {
            log("Removing config file (RPM version) %s\n", magicUsersFileRPM);
            FileUtils.deleteQuietly(magicUsersFileRPM);
        }
        if (magicUsersFileDEB.exists()) {
            log("Removing config file (DEB version) %s\n", magicUsersFileDEB);
            FileUtils.deleteQuietly(magicUsersFileDEB);
        }
    }

    /* (non-Javadoc)
     * @see org.opennms.upgrade.api.OnmsUpgrade#rollback()
     */
    @Override
    public void rollback() throws OnmsUpgradeException {
        if (!canRun()) return;

        File[] files = { magicUsersFile, magicUsersFileRPM, magicUsersFileDEB, usersFile };
        for (File file : files) {
            File zip = new File(file.getAbsolutePath() + ZIP_EXT);
            if (zip.exists()) {
                log("Restoring backup %s\n", zip);
                FileUtils.deleteQuietly(file);
                unzipFile(zip, zip.getParentFile());
            }
        }
    }

    /* (non-Javadoc)
     * @see org.opennms.upgrade.api.OnmsUpgrade#execute()
     */
    @Override
    public void execute() throws OnmsUpgradeException {
        if (!canRun()) {
            log("Error: ignoring the execution of the task because the file magic-users.properties was not found. Maybe the task was already successfully executed before.\n");
            return;
        }

        // Parse read-only attributes
        final List<String> readOnlyUsers = new ArrayList<>();
        try {
            final SAXParserFactory spf = SAXParserFactory.newDefaultInstance();
            spf.setValidating(false);
            final SAXParser parser = spf.newSAXParser();
            parser.parse(usersFile, new DefaultHandler() {
                String userId = null;
                boolean isReadOnly = false;

                StringBuilder sb = null;

                @Override
                public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) throws SAXException {
                    if ("user".equals(qName)) {
                        userId = null;
                        sb = null;
                        int readOnlyIndex = attributes.getIndex("read-only");
                        if (readOnlyIndex >= 0) {
                            final String readOnlyValue = attributes.getValue(readOnlyIndex);
                            isReadOnly = Boolean.parseBoolean(readOnlyValue);
                        }
                    } else if ("user-id".equals(qName)) {
                        sb = new StringBuilder();
                    }
                }
                @Override
                public void endElement (final String uri, final String localName, final String qName) throws SAXException {
                    if ("user".equals(qName)) {
                        if (isReadOnly) {
                            readOnlyUsers.add(userId);
                        }
                        isReadOnly = false;
                        userId = null;
                    } else if ("user-id".equals(qName)) {
                        userId = sb == null? null : sb.toString().trim();
                        sb = null;
                    }
                }
                @Override
                public void characters (final char[] ch, final int start, final int length) throws SAXException {
                    if (sb != null) {
                        sb.append(new String(ch, start, length));
                    }
                }
            });

            if (!readOnlyUsers.isEmpty()) {
                log("Removing the read-only flags from users.xml\n");
                String content = new String(Files.readAllBytes(usersFile.toPath()), StandardCharsets.UTF_8);
                content = content.replaceAll("\\s+read-only=\".+\"", "");
                Files.write(usersFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new OnmsUpgradeException("Can't fix configuration because " + e.getMessage(), e);
        }

        log("Moving security roles into users.xml...\n");
        try {
            UserFactory.init();
            UserManager userManager = UserFactory.getInstance();

            // Retrieve all the currently configured users.
            final List<OnmsUser> users = new ArrayList<>();
            for (final String userName : userManager.getUserNames()) {
                log("Loading configured user: %s...\n", userName);
                users.add(userManager.getOnmsUser(userName));
            }

            // Parse magic-users.properties
            Properties properties = new Properties();
            if (magicUsersFile.exists()) {
                final var is = new FileInputStream(magicUsersFile);
                try (is) {
                    properties.load(is);
                } finally {
                    is.close();
                }
            } else if (magicUsersFileRPM.exists()) {
                final var is = new FileInputStream(magicUsersFileRPM);
                try (is) {
                    properties.load(is);
                } finally {
                    is.close();
                }
            } else if (magicUsersFileDEB.exists()) {
                final var is = new FileInputStream(magicUsersFileDEB);
                try (is) {
                    properties.load(is);
                } finally {
                    is.close();
                }
            } else {
                throw new IllegalArgumentException("Can't find magic-users.properties, or any RPM/DEB backup of it");
            }

            // Look up for custom users and their passwords
            String[] configuredUsers = BundleLists.parseBundleList(properties.getProperty("users"));
            for (String user : configuredUsers) {
                String username = properties.getProperty("user." + user + ".username");
                String password = properties.getProperty("user." + user + ".password");
                OnmsUser newUser = new OnmsUser();
                newUser.setUsername(username);
                newUser.setFullName(user);
                newUser.setComments("This is a system user, do not delete");
                newUser.setPassword(userManager.encryptedPassword(password, true));
                newUser.setPasswordSalted(true);
                users.add(0, newUser);
            }

            // Configure security roles
            String[] configuredRoles = BundleLists.parseBundleList(properties.getProperty("roles"));
            for (final String role : configuredRoles) {
                String userList = properties.getProperty("role." + role + ".users");
                if (userList == null) {
                    log("Warning: Role configuration for '%s' does not have 'users' parameter.  Expecting a 'role.%s.users' property. The role will not be usable.\n", role, role);
                    continue;
                }
                String[] authUsers = BundleLists.parseBundleList(userList);

                boolean notInDefaultGroup = "true".equals(properties.getProperty("role." + role + ".notInDefaultGroup"));
                String securityRole = "ROLE_" + role.toUpperCase();

                List<String> customRoles = new ArrayList<>();
                for (final String username : authUsers) {
                    OnmsUser onmsUser = getUser(users, username);
                    if (onmsUser == null) {
                        log("Warning: User %s doesn't exist on users.xml, Ignoring.\n", username);
                    } else {
                        addRole(onmsUser, securityRole);
                        if (!notInDefaultGroup && !securityRole.equals(Authentication.ROLE_ADMIN)) {
                            addRole(onmsUser, Authentication.ROLE_USER);
                        }
                        if (!Authentication.isValidRole(securityRole)) {
                            log("Warning: %s is a custom role.\n", securityRole);
                            customRoles.add(role);
                        }
                    }
                }

                if (!customRoles.isEmpty()) {
                    String roleList = StringUtils.join(customRoles, ',');
                    log("Creating %s with roles: %s\n", Authentication.ROLE_CONFIGURATION_FILE, roleList);
                    Properties p = new Properties();
                    p.put("roles", roleList);
                    File configFile = new File(ConfigFileConstants.getHome(), "etc" + File.separator + Authentication.ROLE_CONFIGURATION_FILE);
                    p.store(new FileWriter(configFile), "Custom Roles");
                }
            }

            // Update users.xml
            for (final OnmsUser user : users) {
                if (readOnlyUsers.contains(user.getUsername())) {
                    addRole(user, Authentication.ROLE_READONLY);
                    if (!user.getRoles().contains(Authentication.ROLE_USER)) {
                        addRole(user, Authentication.ROLE_USER);
                    }
                }
                userManager.save(user);
            }
        } catch (Throwable e) {
            throw new OnmsUpgradeException("Can't fix configuration because " + e.getMessage(), e);
        }
    }

    /**
     * Gets the user.
     *
     * @param users the users
     * @param userName the user name
     * @return the user
     */
    private OnmsUser getUser(List<OnmsUser> users, String userName) {
        for (final OnmsUser user : users) {
            if (user.getUsername().equals(userName)) {
                return user;
            }
        }
        return null;
    }

    /**
     * Adds the role.
     *
     * @param onmsUser the OpenNMS user
     * @param securityRole the security role
     */
    private void addRole(OnmsUser onmsUser, String securityRole) {
        log("Adding role %s to user %s\n", securityRole, onmsUser.getUsername());
        onmsUser.addRole(securityRole);
    }

    /**
     * Can run.
     *
     * @return true, if successful
     */
    private boolean canRun() {
        boolean defaultOk = magicUsersFile != null && magicUsersFile.exists();
        boolean rpmOk = magicUsersFileRPM != null && magicUsersFileRPM.exists();
        boolean debOk =magicUsersFileDEB != null && magicUsersFileDEB.exists();
        return defaultOk || rpmOk || debOk;
    }
}
