package org.tmatesoft.svn.test;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNUserNameAuthentication;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.DefaultSVNRepositoryPool;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNConflictAction;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnGetInfo;
import org.tmatesoft.svn.core.wc2.SvnInfo;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;

public class InfoTest {
    @Test
    public void testLowLevelInfo() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testLowLevelInfo", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("directory");
            commitBuilder.commit();

            final SVNRepository svnRepository = svnOperationFactory.getRepositoryPool().createRepository(url, true);
            final SVNDirEntry dirEntry = svnRepository.info("", 1);

            Assert.assertEquals(SVNNodeKind.DIR, dirEntry.getKind());
            Assert.assertEquals("", dirEntry.getName());
            Assert.assertEquals("", dirEntry.getRelativePath());
            Assert.assertEquals(1, dirEntry.getRevision());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testLowLevelLatestRevision() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testLowLevelLatestRevision", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String commitAuthor = "author";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setAuthenticationManager(new BasicAuthenticationManager(
                    new SVNAuthentication[]{SVNUserNameAuthentication.newInstance(
                            commitAuthor, false, url, false)}));
            commitBuilder.addFile("directory/file");
            commitBuilder.commit();

            ISVNRepositoryPool repositoryPool = new DefaultSVNRepositoryPool(null, null);
            try {
                final SVNRepository svnRepository = repositoryPool.createRepository(url, true);
                final SVNDirEntry dirEntry = svnRepository.info("directory/file",
                        SVNRevision.HEAD.getNumber());

                Assert.assertEquals(SVNNodeKind.FILE, dirEntry.getKind());
                Assert.assertEquals("file", dirEntry.getName());
                Assert.assertEquals(1, dirEntry.getRevision());
                Assert.assertEquals(commitAuthor, dirEntry.getAuthor());
            } finally {
                repositoryPool.dispose();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testLowLevelInfoDavAccess() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testLowLevelInfoDavAccess", options);
        try {
            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("directory");
            commitBuilder.commit();

            final SVNRepository svnRepository = svnOperationFactory.getRepositoryPool().createRepository(url, true);
            final SVNDirEntry dirEntry = svnRepository.info("", 1);

            Assert.assertEquals(SVNNodeKind.DIR, dirEntry.getKind());
            Assert.assertEquals("", dirEntry.getName());
            Assert.assertEquals("", dirEntry.getRelativePath());
            Assert.assertEquals(1, dirEntry.getRevision());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testPegRevisionIsConsideredForRemoteInfo() throws Exception {
        //SVNKIT-272
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testPegRevisionIsConsideredForRemoteInfo", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addDirectory("directory");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("directory");
            commitBuilder2.commit();

            final SVNURL directoryUrl = url.appendPath("directory", false);

            final SvnGetInfo getInfo = svnOperationFactory.createGetInfo();
            getInfo.setSingleTarget(SvnTarget.fromURL(directoryUrl, SVNRevision.create(1)));
            final SvnInfo info = getInfo.run();

            Assert.assertEquals(1, info.getRevision());
            Assert.assertEquals(1, info.getLastChangedRevision());
            Assert.assertEquals(directoryUrl, info.getUrl());
            Assert.assertEquals(url, info.getRepositoryRootUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testPegRevisionIsConsideredForLocalInfo() throws Exception {
        //SVNKIT-272
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testPegRevisionIsConsideredForLocalInfo", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addDirectory("directory");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("directory");
            commitBuilder2.commit();

            final SVNURL directoryUrl = url.appendPath("directory", false);

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File directory = workingCopy.getFile("directory");

            final SvnGetInfo getInfo = svnOperationFactory.createGetInfo();
            getInfo.setSingleTarget(SvnTarget.fromFile(directory, SVNRevision.create(1)));
            final SvnInfo info = getInfo.run();

            Assert.assertEquals(1, info.getRevision());
            Assert.assertEquals(1, info.getLastChangedRevision());
            Assert.assertEquals(directoryUrl, info.getUrl());
            Assert.assertEquals(url, info.getRepositoryRootUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testBackslashInFilename() throws Exception {
        //SVNKIT-332
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testBackslashInFilename", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file\\with\\backslash");
            commitBuilder.commit();

            final  SVNURL fileUrl = url.appendPath("file\\with\\backslash", false);

            final SvnGetInfo getInfo = svnOperationFactory.createGetInfo();
            getInfo.setSingleTarget(SvnTarget.fromURL(fileUrl));
            final SvnInfo info = getInfo.run();

            Assert.assertEquals(1, info.getRevision());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUnversiondeObstructionWC17() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUnversiondeObstructionWC17", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addDirectory("directory");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.addFile("directory/file");
            commitBuilder2.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.setTargetWorkingCopyFormat(ISVNWCDb.WC_FORMAT_17);
            checkout.setRevision(SVNRevision.create(1));
            checkout.run();

            final File directory = new File(workingCopyDirectory, "directory");
            final File file = new File(directory, "file");

            SVNFileUtil.deleteFile(file);
            SVNFileUtil.ensureDirectoryExists(file);

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final Map<File,SvnInfo> infos = new HashMap<File, SvnInfo>();

            final SvnGetInfo getInfo = svnOperationFactory.createGetInfo();
            getInfo.setFetchActualOnly(true);
            getInfo.setDepth(SVNDepth.INFINITY);
            getInfo.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            getInfo.setReceiver(new ISvnObjectReceiver<SvnInfo>() {
                public void receive(SvnTarget target, SvnInfo info) throws SVNException {
                    infos.put(target.getFile(), info);
                }
            });
            getInfo.run();

            Assert.assertEquals(3, infos.size());
            final Collection<SVNConflictDescription> conflicts = infos.get(file).getWcInfo().getConflicts();
            Assert.assertEquals(1, conflicts.size());
            final SVNConflictDescription conflictDescription = conflicts.iterator().next();
            Assert.assertTrue(conflictDescription.isTreeConflict());
            Assert.assertEquals(SVNConflictReason.UNVERSIONED, conflictDescription.getConflictReason());
            Assert.assertEquals(SVNConflictAction.ADD, conflictDescription.getConflictAction());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testWorkingCopyFileSize() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testWorkingCopyFileSize", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File file = workingCopy.getFile("file");

            final SvnGetInfo getInfo1 = svnOperationFactory.createGetInfo();
            getInfo1.setSingleTarget(SvnTarget.fromFile(file));
            final SvnInfo info1 = getInfo1.run();

            TestUtil.writeFileContentsString(file, "content");

            final SvnGetInfo getInfo2 = svnOperationFactory.createGetInfo();
            getInfo2.setSingleTarget(SvnTarget.fromFile(file));
            final SvnInfo info2 = getInfo2.run();

            Assert.assertEquals(ISVNWCDb.INVALID_FILESIZE, info1.getSize());
            Assert.assertEquals(ISVNWCDb.INVALID_FILESIZE, info2.getSize());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "InfoTest";
    }
}
