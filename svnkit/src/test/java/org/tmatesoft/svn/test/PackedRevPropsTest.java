package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSFile;
import org.tmatesoft.svn.core.internal.io.fs.FSPacker;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.ISVNChangeEntryHandler;
import org.tmatesoft.svn.core.wc.admin.SVNChangeEntry;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;
import org.tmatesoft.svn.core.wc2.SvnGetProperties;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

public class PackedRevPropsTest {

    @Test
    public void testPackFSFSRepository() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testPackFSFSRepository", options);
        try {
            final File repositoryRoot = sandbox.createDirectory("svn.repo");
            SVNURL url = SVNRepositoryFactory.createLocalRepository(repositoryRoot, null, true,
                    false, false, false, false, false, true);

            updateMaxFilesPerDirectory(repositoryRoot);

            for (int i = 0; i < 20; i++) {
                createCommitThatAddsFile(url, "file" + i);
            }
            final SVNRepository svnRepository = SVNRepositoryFactory.create(url);
            try {
                for (int i = 0; i <= 10; i++) {
                    svnRepository.setRevisionPropertyValue(i, "test" + i, SVNPropertyValue.create("value" + i));
                }
                final FSFS fsfs = new FSFS(repositoryRoot);
                fsfs.open();
                new FSPacker(null).pack(fsfs);
                fsfs.close();

                for (int i = 0; i <= 10; i++) {
                    final SVNPropertyValue propertyValue = svnRepository.getRevisionPropertyValue(i, "test" + i);
                    Assert.assertEquals("value" + i, SVNPropertyValue.getPropertyAsString(propertyValue));
                }
            } finally {
                svnRepository.closeSession();
            }

        } finally {
            sandbox.dispose();
        }
    }

    @Test
    public void testPackAndCompressFSFSRepository() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testPackAndCompressFSFSRepository", options);
        try {
            final File repositoryRoot = sandbox.createDirectory("svn.repo");
            SVNURL url = SVNRepositoryFactory.createLocalRepository(repositoryRoot, null, true,
                    false, false, false, false, false, true);

            updateCompressedFlag(repositoryRoot, true);
            updateMaxFilesPerDirectory(repositoryRoot);

            for (int i = 0; i < 20; i++) {
                createCommitThatAddsFile(url, "file" + i);
            }
            final SVNRepository svnRepository = SVNRepositoryFactory.create(url);
            try {
                for (int i = 0; i <= 10; i++) {
                    svnRepository.setRevisionPropertyValue(i, "test" + i, SVNPropertyValue.create("value" + i));
                }
                final FSFS fsfs = new FSFS(repositoryRoot);
                fsfs.open();
                new FSPacker(null).pack(fsfs);
                fsfs.close();

                for (int i = 0; i <= 10; i++) {
                    final SVNPropertyValue propertyValue = svnRepository.getRevisionPropertyValue(i, "test" + i);
                    Assert.assertEquals("value" + i, SVNPropertyValue.getPropertyAsString(propertyValue));
                }
                for (int i = 11; i <= 20; i++) {
                    svnRepository.setRevisionPropertyValue(i, "test" + i, SVNPropertyValue.create("value" + i));
                    final SVNPropertyValue propertyValue = svnRepository.getRevisionPropertyValue(i, "test" + i);
                    Assert.assertEquals("value" + i, SVNPropertyValue.getPropertyAsString(propertyValue));
                }
            } finally {
                svnRepository.closeSession();
            }

        } finally {
            sandbox.dispose();
        }
    }

    @Test
    public void testDeltaSelfRepresentationHeader() throws Exception {
        //SVNKIT-504
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeNotNull(options.getSvnCommand());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDeltaRepresentationHeader", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String enableDirDeltification = "enable-dir-deltification";
            final String enablePropsDeltification = "enable-props-deltification";

            final File repositoryRoot = new File(url.getPath());
            final File dbDirectory = new File(repositoryRoot, FSFS.DB_DIR);
            final File fsfsConfigFile = new File(dbDirectory, FSFS.PATH_CONFIG);

            final SVNConfigFile fsfsConfig = new SVNConfigFile(fsfsConfigFile);
            fsfsConfig.setPropertyValue("deltification", enableDirDeltification, "true", false);
            fsfsConfig.setPropertyValue("deltification", enablePropsDeltification, "true", false);
            fsfsConfig.save();

            final String svnCommand = options.getSvnCommand();
            SVNFileUtil.execCommand(new String[] {svnCommand, "mkdir", url.appendPath("trunk", false).toString(), "-m", "m"});

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            SVNFileUtil.execCommand(new String[] {svnCommand, "checkout", url.toString(), workingCopyDirectory.getAbsolutePath()});
            SVNFileUtil.execCommand(new String[] {svnCommand, "propset", "propertyName", "propertyValue", new File(workingCopyDirectory, "trunk").getAbsolutePath()});
            SVNFileUtil.execCommand(new String[] {svnCommand, "commit", "-m", "m", workingCopyDirectory.getAbsolutePath()});

            final SvnGetProperties getProperties = svnOperationFactory.createGetProperties();
            getProperties.setSingleTarget(SvnTarget.fromURL(url.appendPath("trunk", false)));
            final SVNProperties properties = getProperties.run();

            Assert.assertNotNull(properties);
            Assert.assertEquals(SVNPropertyValue.create("propertyValue"), properties.getSVNPropertyValue("propertyName"));

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDeltaRepresentationHeader() throws Exception {
        //SVNKIT-504
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeNotNull(options.getSvnCommand());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDeltaRepresentationHeader", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String enableDirDeltification = "enable-dir-deltification";
            final String enablePropsDeltification = "enable-props-deltification";

            final File repositoryRoot = new File(url.getPath());
            final File dbDirectory = new File(repositoryRoot, FSFS.DB_DIR);
            final File fsfsConfigFile = new File(dbDirectory, FSFS.PATH_CONFIG);

            final SVNConfigFile fsfsConfig = new SVNConfigFile(fsfsConfigFile);
            fsfsConfig.setPropertyValue("deltification", enableDirDeltification, "true", false);
            fsfsConfig.setPropertyValue("deltification", enablePropsDeltification, "true", false);
            fsfsConfig.save();

            final String svnCommand = options.getSvnCommand();
            SVNFileUtil.execCommand(new String[] {svnCommand, "mkdir", url.appendPath("trunk", false).toString(), "-m", "m"});

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            final String longIncompressibleValue1 = "just long incompressible value asljfsiodfnaidubfasdifbasdkfsdkfasdbfakubfdakkysbfsdyfbsadfbasydbfuasydf";
            final String longIncompressibleValue2 = "another long incompressible value aopdsfjadsfdashfuihasdflusdhfuisahdfilsadfuhsldaufhsahufihaufihwbefubweuf";

            SVNFileUtil.execCommand(new String[] {svnCommand, "checkout", url.toString(), workingCopyDirectory.getAbsolutePath()});
            SVNFileUtil.execCommand(new String[] {svnCommand, "propset", "propertyName1",
                    longIncompressibleValue1, new File(workingCopyDirectory, "trunk").getAbsolutePath()});
            SVNFileUtil.execCommand(new String[] {svnCommand, "commit", "-m", "m", workingCopyDirectory.getAbsolutePath()});
            SVNFileUtil.execCommand(new String[] {svnCommand, "propset", "propertyName2",
                    longIncompressibleValue2, new File(workingCopyDirectory, "trunk").getAbsolutePath()});
            SVNFileUtil.execCommand(new String[] {svnCommand, "commit", "-m", "m", workingCopyDirectory.getAbsolutePath()});

            final SvnGetProperties getProperties = svnOperationFactory.createGetProperties();
            getProperties.setSingleTarget(SvnTarget.fromURL(url.appendPath("trunk", false)));
            final SVNProperties properties = getProperties.run();

            Assert.assertNotNull(properties);
            Assert.assertEquals(SVNPropertyValue.create(longIncompressibleValue1), properties.getSVNPropertyValue("propertyName1"));
            Assert.assertEquals(SVNPropertyValue.create(longIncompressibleValue2), properties.getSVNPropertyValue("propertyName2"));
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private void updateCompressedFlag(File repositoryRoot, boolean compressed) throws SVNException {
        final FSFS fsfs = new FSFS(repositoryRoot);
        fsfs.open();
        final File configFile = fsfs.getConfigFile();
        SVNConfigFile config = new SVNConfigFile(configFile);
        config.setPropertyValue(FSFS.PACKED_REVPROPS_SECTION, FSFS.COMPRESS_PACKED_REVPROPS_OPTION, String.valueOf(compressed), true);
        fsfs.close();
    }

    private void updateMaxFilesPerDirectory(File repositoryRoot) throws SVNException {
        final FSFS fsfs = new FSFS(repositoryRoot);
        fsfs.open();
        fsfs.writeDBFormat(fsfs.getDBFormat(), 10, true);
        fsfs.close();
    }

    private void createCommitThatAddsFile(SVNURL url, String filename) throws SVNException {
        final CommitBuilder commitBuilder = new CommitBuilder(url);
        commitBuilder.addFile(filename);
        commitBuilder.commit();
    }

    private String getTestName() {
        return getClass().getSimpleName();
    }
}
