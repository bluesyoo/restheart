/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.softinstigate.restheart;

import com.mongodb.MongoClient;
import com.softinstigate.restheart.db.PropsFixer;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import com.softinstigate.restheart.security.handlers.AccessManagerHandler;
import com.softinstigate.restheart.handlers.ErrorHandler;
import com.softinstigate.restheart.handlers.GzipEncodingHandler;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.handlers.RequestDispacherHandler;
import com.softinstigate.restheart.handlers.injectors.RequestContextInjectorHandler;
import com.softinstigate.restheart.handlers.root.GetRootHandler;
import com.softinstigate.restheart.handlers.collection.DeleteCollectionHandler;
import com.softinstigate.restheart.handlers.collection.GetCollectionHandler;
import com.softinstigate.restheart.handlers.injectors.CollectionPropsInjectorHandler;
import com.softinstigate.restheart.handlers.injectors.DbPropsInjectorHandler;
import com.softinstigate.restheart.handlers.injectors.LocalCachesSingleton;
import com.softinstigate.restheart.handlers.collection.PatchCollectionHandler;
import com.softinstigate.restheart.handlers.collection.PostCollectionHandler;
import com.softinstigate.restheart.handlers.collection.PutCollectionHandler;
import com.softinstigate.restheart.handlers.database.DeleteDBHandler;
import com.softinstigate.restheart.handlers.database.GetDBHandler;
import com.softinstigate.restheart.handlers.database.PatchDBHandler;
import com.softinstigate.restheart.handlers.database.PutDBHandler;
import com.softinstigate.restheart.handlers.document.DeleteDocumentHandler;
import com.softinstigate.restheart.handlers.document.GetDocumentHandler;
import com.softinstigate.restheart.handlers.document.PatchDocumentHandler;
import com.softinstigate.restheart.handlers.document.PutDocumentHandler;
import com.softinstigate.restheart.handlers.indexes.DeleteIndexHandler;
import com.softinstigate.restheart.handlers.indexes.GetIndexesHandler;
import com.softinstigate.restheart.handlers.indexes.PutIndexHandler;
import com.softinstigate.restheart.security.AccessManager;
import com.softinstigate.restheart.security.handlers.PredicateAuthenticationConstraintHandler;
import com.softinstigate.restheart.utils.ResourcesExtractor;
import com.softinstigate.restheart.utils.LoggingInitializer;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.handlers.applicationlogic.ApplicationLogicHandler;
import com.softinstigate.restheart.handlers.OptionsHandler;
import com.softinstigate.restheart.handlers.PipedWrappingHandler;
import com.softinstigate.restheart.handlers.injectors.BodyInjectorHandler;
import com.softinstigate.restheart.handlers.metadata.MetadataEnforcerHandler;
import static com.softinstigate.restheart.security.RestheartIdentityManager.RESTHEART_REALM;
import com.softinstigate.restheart.security.handlers.CORSHandler;
import io.undertow.Undertow;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.handlers.HttpContinueAcceptingHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import static io.undertow.Handlers.resource;
import static io.undertow.Handlers.path;
import io.undertow.Undertow.Builder;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.AllowedMethodsHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.RequestLimit;
import io.undertow.server.handlers.RequestLimitingHandler;
import io.undertow.util.HttpString;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class Bootstrapper
{
    private static Undertow server;

    private static final Logger logger = LoggerFactory.getLogger(Bootstrapper.class);

    private static File browserRootFile = null;

    private static GracefulShutdownHandler hanldersPipe = null;

    private static Configuration conf;

    public static void start(String confFilePath)
    {
        String[] args = new String[1];

        args[0] = confFilePath;

        main(args);
    }

    public static void shutdown()
    {
        System.exit(0);
    }

    public static void main(final String[] args)
    {
        if (args == null || args.length < 1)
        {
            conf = new Configuration();
        }
        else
        {
            conf = new Configuration(args[0]);
        }

        LoggingInitializer.setLogLevel(conf.getLogLevel());

        if (conf.isLogToFile())
        {
            LoggingInitializer.startFileLogging(conf.getLogFilePath());
        }

        logger.info("starting restheart ********************************************");

        String mongoHosts = conf.getMongoServers().stream().map(s -> s.get(Configuration.MONGO_HOST) + ":" + s.get(Configuration.MONGO_PORT) + " ").reduce("", String::concat);

        logger.info("initializing mongodb connection pool to {}", mongoHosts);

        try
        {
            MongoDBClientSingleton.init(conf);

            logger.info("mongodb connection pool initialized");

            PropsFixer.fixAllMissingProps();
        }
        catch (Throwable t)
        {
            logger.error("error connecting to mongodb. exiting..", t);
            System.exit(-1);
        }

        try
        {
            start();
        }
        catch (Throwable t)
        {
            logger.error("error starting restheart. exiting..", t);
            System.exit(-2);
        }

        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
                logger.info("restheart stopping");
                logger.info("waiting for pending request to complete (up to 1 minute)");

                try
                {
                    hanldersPipe.shutdown();
                    hanldersPipe.awaitShutdown(60 * 1000); // up to 1 minute
                }
                catch (InterruptedException ie)
                {
                    logger.error("error while waiting for pending request to complete", ie);
                }

                if (server != null)
                {
                    try
                    {
                        server.stop();
                    }
                    catch (Throwable t)
                    {
                        logger.error("error stopping undertow server", t);
                    }
                }

                try
                {
                    MongoClient client = MongoDBClientSingleton.getInstance().getClient();
                    client.fsync(false);
                    client.close();
                }
                catch (Throwable t)
                {
                    logger.error("error flushing and clonsing the mongo cliet", t);
                }

                if (browserRootFile != null)
                {
                    try
                    {
                        ResourcesExtractor.deleteTempDir("browser", browserRootFile);
                    }
                    catch (URISyntaxException | IOException ex)
                    {
                        logger.error("error cleaning up temporary directory {}", browserRootFile.toString(), ex);
                    }
                }

                logger.info("restheart stopped");
            }
        });

        if (conf.isLogToFile())
        {
            logger.info("logging to {} with level {}", conf.getLogFilePath(), conf.getLogLevel());
        }

        if (!conf.isLogToConsole())
        {
            logger.info("stopping logging to console ");
            LoggingInitializer.stopConsoleLogging();
        }
        else
        {
            logger.info("logging to console with level {}", conf.getLogLevel());
        }

        logger.info("restheart started **********************************************");
    }

    private static void start()
    {
        if (conf == null)
        {
            logger.error("no configuration found. exiting..");
            System.exit(-1);
        }

        if (!conf.isHttpsListener() && !conf.isHttpListener() && !conf.isAjpListener())
        {
            logger.error("no listener specified. exiting..");
            System.exit(-1);
        }

        IdentityManager identityManager = null;

        if (conf.getIdmImpl() == null)
        {
            logger.warn("***** no identity manager specified. authentication disabled.");
            identityManager = null;
        }
        else
        {
            try
            {
                Object idm = Class.forName(conf.getIdmImpl()).getConstructor(Map.class).newInstance(conf.getIdmArgs());
                identityManager = (IdentityManager) idm;
            }
            catch (ClassCastException | NoSuchMethodException | SecurityException | ClassNotFoundException | IllegalArgumentException | InstantiationException | IllegalAccessException | InvocationTargetException ex)
            {
                logger.error("error configuring idm implementation {}", conf.getIdmImpl(), ex);
                System.exit(-3);
            }
        }

        AccessManager accessManager = null;

        if (conf.getAmImpl() == null && conf.getIdmImpl() != null)
        {
            logger.warn("***** no access manager specified. authenticated users can do anything.");
            accessManager = null;
        }
        else if (conf.getAmImpl() == null && conf.getIdmImpl() == null)
        {
            logger.warn("***** no access manager specified. users can do anything.");
            accessManager = null;
        }
        else
        {
            try
            {
                Object am = Class.forName(conf.getAmImpl()).getConstructor(Map.class).newInstance(conf.getAmArgs());
                accessManager = (AccessManager) am;
            }
            catch (ClassCastException | NoSuchMethodException | SecurityException | ClassNotFoundException | IllegalArgumentException | InstantiationException | IllegalAccessException | InvocationTargetException ex)
            {
                logger.error("error configuring acess manager implementation {}", conf.getAmImpl(), ex);
                System.exit(-3);
            }
        }

        SSLContext sslContext = null;

        try
        {
            KeyManagerFactory kmf;
            KeyStore ks;

            if (conf.isUseEmbeddedKeystore())
            {
                char[] storepass = "restheart".toCharArray();
                char[] keypass = "restheart".toCharArray();

                String storename = "rakeystore.jks";

                sslContext = SSLContext.getInstance("TLS");
                kmf = KeyManagerFactory.getInstance("SunX509");
                ks = KeyStore.getInstance("JKS");
                ks.load(Bootstrapper.class.getClassLoader().getResourceAsStream(storename), storepass);

                kmf.init(ks, keypass);
                sslContext.init(kmf.getKeyManagers(), null, null);
            }
            else
            {
                sslContext = SSLContext.getInstance("TLS");
                kmf = KeyManagerFactory.getInstance("SunX509");
                ks = KeyStore.getInstance("JKS");

                FileInputStream fis = new FileInputStream(new File(conf.getKeystoreFile()));

                ks.load(fis, conf.getKeystorePassword().toCharArray());

                kmf.init(ks, conf.getCertPassword().toCharArray());
                sslContext.init(kmf.getKeyManagers(), null, null);
            }
        }
        catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException | CertificateException | UnrecoverableKeyException ex)
        {
            logger.error("couldn't start restheart, error with specified keystore. exiting..", ex);
            System.exit(-1);
        }
        catch (FileNotFoundException ex)
        {
            logger.error("couldn't start restheart, keystore file not found. exiting..", ex);
            System.exit(-1);
        }
        catch (IOException ex)
        {
            logger.error("couldn't start restheart, error reading the keystore file. exiting..", ex);
            System.exit(-1);
        }

        try
        {
            browserRootFile = ResourcesExtractor.extract("browser");
        }
        catch (URISyntaxException | IOException ex)
        {
            logger.error("error instanitating browser web app. exiting..", ex);
            System.exit(-1);
        }

        logger.info("static resources are in {}", browserRootFile.toString());

        Builder builder = Undertow.builder();

        if (conf.isHttpsListener())
        {
            builder.addHttpsListener(conf.getHttpsPort(), conf.getHttpHost(), sslContext);
            logger.info("https listener bound at {}:{}", conf.getHttpsHost(), conf.getHttpsPort());
        }

        if (conf.isHttpListener())
        {
            builder.addHttpListener(conf.getHttpPort(), conf.getHttpsHost());
            logger.info("http listener bound at {}:{}", conf.getHttpHost(), conf.getHttpPort());
        }

        if (conf.isAjpListener())
        {
            builder.addAjpListener(conf.getAjpPort(), conf.getAjpHost());
            logger.info("ajp listener bound at {}:{}", conf.getAjpHost(), conf.getAjpPort());
        }

        LocalCachesSingleton.init(conf);

        if (conf.isLocalCacheEnabled())
        {
            logger.info("local cache enabled");
        }
        else
        {
            logger.info("local cache not enabled");
        }

        hanldersPipe = getHandlersPipe(identityManager, accessManager);

        builder
                .setIoThreads(conf.getIoThreads())
                .setWorkerThreads(conf.getWorkerThreads())
                .setDirectBuffers(conf.isDirectBuffers())
                .setBufferSize(conf.getBufferSize())
                .setBuffersPerRegion(conf.getBuffersPerRegion())
                .setHandler(hanldersPipe);

        builder.build().start();
    }

    private static GracefulShutdownHandler getHandlersPipe(IdentityManager identityManager, AccessManager accessManager)
    {
        PipedHttpHandler coreHanlderChain
                = new DbPropsInjectorHandler(
                        new CollectionPropsInjectorHandler(
                                new BodyInjectorHandler(
                                        new MetadataEnforcerHandler(
                                                new RequestDispacherHandler(
                                                        new GetRootHandler(),
                                                        new GetDBHandler(),
                                                        new PutDBHandler(),
                                                        new DeleteDBHandler(),
                                                        new PatchDBHandler(),
                                                        new GetCollectionHandler(),
                                                        new PostCollectionHandler(),
                                                        new PutCollectionHandler(),
                                                        new DeleteCollectionHandler(),
                                                        new PatchCollectionHandler(),
                                                        new GetDocumentHandler(),
                                                        new PutDocumentHandler(),
                                                        new DeleteDocumentHandler(),
                                                        new PatchDocumentHandler(),
                                                        new GetIndexesHandler(),
                                                        new PutIndexHandler(),
                                                        new DeleteIndexHandler()
                                                )
                                        )
                                )
                        )
                );

        PathHandler paths = path()
                .addPrefixPath("/_browser", resource(new FileResourceManager(browserRootFile, 3)).addWelcomeFiles("browser.html").setDirectoryListingEnabled(false));

        conf.getMongoMounts().stream().forEach(m ->
        {
            String url = (String) m.get(Configuration.MONGO_MOUNT_WHERE);
            String db = (String) m.get(Configuration.MONGO_MOUNT_WHAT);

            paths.addPrefixPath(url, new CORSHandler(new RequestContextInjectorHandler(url, db, new OptionsHandler(addSecurity(coreHanlderChain, identityManager, accessManager)))));

            logger.info("url {} bound to resource {}", url, db);
        });

        if (conf.getApplicationLogicMounts() != null)
        {
            conf.getApplicationLogicMounts().stream().forEach(al ->
            {
                try
                {
                    String alClazz = (String) al.get(Configuration.APPLICATION_LOGIC_MOUNT_WHAT);
                    String alWhere = (String) al.get(Configuration.APPLICATION_LOGIC_MOUNT_WHERE);
                    boolean alSecured = (Boolean) al.get(Configuration.APPLICATION_LOGIC_MOUNT_SECURED);
                    Object alArgs = al.get(Configuration.APPLICATION_LOGIC_MOUNT_ARGS);

                    if (alWhere == null || !alWhere.startsWith("/"))
                    {
                        logger.error("cannot pipe application logic handler {}. parameter 'where' must start with /", alWhere);
                        return;
                    }

                    if (alArgs != null && !(alArgs instanceof Map))
                    {
                        logger.error("cannot pipe application logic handler {}. args are not defined as a map. it is a ", alWhere, alWhere.getClass());
                        return;
                    }

                    Object o = Class.forName(alClazz).getConstructor(PipedHttpHandler.class, Map.class).newInstance(null, (Map) alArgs);

                    if (o instanceof ApplicationLogicHandler)
                    {
                        ApplicationLogicHandler alHandler = (ApplicationLogicHandler) o;

                        PipedHttpHandler handler = new CORSHandler(new RequestContextInjectorHandler("/_logic", "*", alHandler));

                        if (alSecured)
                        {
                            paths.addPrefixPath("/_logic" + alWhere, addSecurity(handler, identityManager, accessManager));
                        }
                        else
                        {
                            paths.addPrefixPath("/_logic" + alWhere, handler);
                        }

                        logger.info("url {} bound to application logic handler {}. access manager: {}", "/_logic" + alWhere, alClazz, alSecured);
                    }
                    else
                    {
                        logger.error("cannot pipe application logic handler {}. class {} does not extend ApplicationLogicHandler", alWhere, alClazz);
                    }

                }
                catch (Throwable t)
                {
                    logger.error("cannot pipe application logic handler {}", al.get(Configuration.APPLICATION_LOGIC_MOUNT_WHERE), t);
                }
            });
        }

        return new GracefulShutdownHandler(
                new RequestLimitingHandler(new RequestLimit(conf.getRequestLimit()),
                        new AllowedMethodsHandler(
                                new BlockingHandler(
                                        new GzipEncodingHandler(
                                                new ErrorHandler(
                                                        new HttpContinueAcceptingHandler(paths)
                                                ), conf.isForceGzipEncoding()
                                        )
                                ), // allowed methods
                                HttpString.tryFromString(RequestContext.METHOD.GET.name()),
                                HttpString.tryFromString(RequestContext.METHOD.POST.name()),
                                HttpString.tryFromString(RequestContext.METHOD.PUT.name()),
                                HttpString.tryFromString(RequestContext.METHOD.DELETE.name()),
                                HttpString.tryFromString(RequestContext.METHOD.PATCH.name()),
                                HttpString.tryFromString(RequestContext.METHOD.OPTIONS.name())
                        )
                )
        );
    }

    private static PipedHttpHandler addSecurity(final PipedHttpHandler toSecure, final IdentityManager identityManager, final AccessManager accessManager)
    {
        if (identityManager != null)
        {
            HttpHandler handler = null;
            
            if (accessManager != null)
            {
                handler = new AccessManagerHandler(accessManager, null);
            }

            handler = new AuthenticationCallHandler(handler);
            handler = new PredicateAuthenticationConstraintHandler(handler, accessManager);
            final List<AuthenticationMechanism> mechanisms = Collections.<AuthenticationMechanism>singletonList(new BasicAuthenticationMechanism(RESTHEART_REALM, "BASIC", true));
            handler = new AuthenticationMechanismsHandler(handler, mechanisms);
            handler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, identityManager, handler);

            return new PipedWrappingHandler(toSecure, handler);
        }
        else
        {
            return toSecure;
        }
    }

    /**
     * @return the conf
     */
    public static Configuration getConfiguration()
    {
        return conf;
    }
}
