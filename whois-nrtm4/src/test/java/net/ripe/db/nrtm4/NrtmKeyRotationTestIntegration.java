package net.ripe.db.nrtm4;

import net.ripe.db.nrtm4.domain.NrtmKeyRecord;
import net.ripe.db.nrtm4.domain.UpdateNotificationFile;
import net.ripe.db.nrtm4.util.JWSUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;

import static net.ripe.db.whois.query.support.PatternMatcher.matchesPattern;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@Tag("IntegrationTest")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class NrtmKeyRotationTestIntegration extends AbstractNrtmIntegrationTest {

    @Test
    public void should_not_generate_next_signing_key_in_notification_file()  {
        setTime(LocalDateTime.now().minusDays(1));

        snapshotFileGenerator.createSnapshot();

        setTime(LocalDateTime.now().plusYears(1).minusDays(9));

        updateNotificationFileGenerator.generateFile();

        final UpdateNotificationFile testIteration = getNotificationFileBySource("TEST");
        final UpdateNotificationFile testNonAuthIteration = getNotificationFileBySource("TEST-NONAUTH");

        assertThat(testIteration.getSource().getName(), is("TEST"));
        assertThat(testIteration.getNextSigningKey(), is(nullValue()));

        assertThat(testNonAuthIteration.getSource().getName(), is("TEST-NONAUTH"));
        assertThat(testNonAuthIteration.getNextSigningKey(), is(nullValue()));
    }

    @Test
    public void should_add_next_signing_key_in_notification_file()  {
        setTime(LocalDateTime.now().minusDays(1));

        snapshotFileGenerator.createSnapshot();

        setTime(LocalDateTime.now().plusYears(1).minusDays(7));

        nrtmKeyPairService.generateOrRotateNextKey();
        addPublicKeyinPemFormat(nrtmKeyPairService.getNextkeyPair().id());
        updateNotificationFileGenerator.generateFile();

        final UpdateNotificationFile testIteration = getNotificationFileBySource("TEST");
        final UpdateNotificationFile testNonAuthIteration = getNotificationFileBySource("TEST-NONAUTH");


        final String nextKey = nrtmKeyPairService.getNextkeyPair().pemFormat();
        assertThat(testIteration.getSource().getName(), is("TEST"));
        assertThat(testIteration.getNextSigningKey(), is(nextKey));

        assertThat(testNonAuthIteration.getSource().getName(), is("TEST-NONAUTH"));
        assertThat(testNonAuthIteration.getNextSigningKey(), is(nextKey));
    }

    @Test
    public void should_rotate_next_key_as_new_key()  {

        //No new signing next key till expiry is greater than 7 days
        setTime(LocalDateTime.now());

        snapshotFileGenerator.createSnapshot();
        nrtmKeyPairService.generateOrRotateNextKey();

        assertThat(nrtmKeyPairService.getNextkeyPair(), is(nullValue()));

        //New signing next key when expiry is smaller than 7 days
        setTime(LocalDateTime.now().plusYears(1).minusDays(7));

        nrtmKeyPairService.generateOrRotateNextKey();

        final String nextKey = nrtmKeyPairService.getNextkeyPair().pemFormat();
        assertThat(nrtmKeyPairService.getNextkeyPair(), is(not(nullValue())));

        //New signing next key will be the active key now and no next signing key
        setTime(LocalDateTime.now().plusYears(1));
        nrtmKeyPairService.generateOrRotateNextKey();

        final String newCurrentKey = nrtmKeyConfigDao.getActiveKeyPair().pemFormat();
        assertThat(nextKey, is(newCurrentKey));
        assertThat(nrtmKeyPairService.getNextkeyPair(), is(nullValue()));
    }

    @Test
    public void should_force_rotate()  {

        //No new signing next key till expiry is greater than 7 days
        setTime(LocalDateTime.now());

        snapshotFileGenerator.createSnapshot();
        nrtmKeyPairService.generateOrRotateNextKey();

        assertThat(nrtmKeyPairService.getNextkeyPair(), is(nullValue()));

        //New signing next key when expiry is smaller than 7 days
        setTime(LocalDateTime.now().plusYears(1).minusDays(7));

        nrtmKeyPairService.generateOrRotateNextKey();

        assertThat(nrtmKeyPairService.getNextkeyPair(), is(not(nullValue())));

        final String currentActiveKey = nrtmKeyConfigDao.getActiveKeyPair().pemFormat();

        nrtmKeyPairService.deleteAndGenerateNewActiveKey();

        final String newActiveKey = nrtmKeyConfigDao.getActiveKeyPair().pemFormat();

        assertThat(newActiveKey , is(not(currentActiveKey)));
        assertThat(nrtmKeyPairService.getNextkeyPair(), is(nullValue()));

    }

    @Test
    public void should_make_next_key_as_active()  {

        //No new signing next key till expiry is greater than 7 days
        setTime(LocalDateTime.now());

        snapshotFileGenerator.createSnapshot();
        nrtmKeyPairService.generateOrRotateNextKey();

        assertThat(nrtmKeyPairService.getNextkeyPair(), is(nullValue()));

        //New signing next key when expiry is smaller than 7 days
        setTime(LocalDateTime.now().plusYears(1).minusDays(7));

        nrtmKeyPairService.generateOrRotateNextKey();

        assertThat(nrtmKeyPairService.getNextkeyPair(), is(not(nullValue())));

        final String currentActiveKey = nrtmKeyConfigDao.getActiveKeyPair().pemFormat();
        final String nextKey = nrtmKeyPairService.getNextkeyPair().pemFormat();

        nrtmKeyPairService.forceRotateKey();

        final String newActiveKey = nrtmKeyConfigDao.getActiveKeyPair().pemFormat();

        assertThat(newActiveKey , is(nextKey));
        assertThat(nrtmKeyPairService.getNextkeyPair(), is(nullValue()));

    }

    @Test
    public void should_get_next_key_from_multiple_inactive_key()  {
        setTime(LocalDateTime.now());
        nrtmKeyPairService.generateKeyRecord(false);

        final NrtmKeyRecord oldestKey = nrtmKeyConfigDao.getAllKeyPair().get(0);

        nrtmKeyPairService.generateKeyRecord(true);

        setTime(LocalDateTime.now().plusYears(1).plusMonths(10));
        nrtmKeyPairService.generateKeyRecord(false);

        setTime(LocalDateTime.now().plusYears(1).minusDays(7));

        final NrtmKeyRecord expectedNextKey = nrtmKeyConfigDao.getAllKeyPair().stream().filter( nrtmKeyRecord -> nrtmKeyRecord.isActive() == false && nrtmKeyRecord.id() != oldestKey.id()).findFirst().get();
        nrtmKeyPairService.generateOrRotateNextKey();

        assertThat(expectedNextKey.id(), is(nrtmKeyPairService.getNextkeyPair().id()));
    }
}
