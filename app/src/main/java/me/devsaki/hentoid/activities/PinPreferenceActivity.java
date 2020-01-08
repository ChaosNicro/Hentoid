package me.devsaki.hentoid.activities;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import me.devsaki.hentoid.fragments.pin.ActivatedPinPreferenceFragment;
import me.devsaki.hentoid.fragments.pin.DeactivatedPinPreferenceFragment;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;

public class PinPreferenceActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            boolean isLockOn = !Preferences.getAppLockPin().isEmpty();
            Fragment fragment = isLockOn ? new ActivatedPinPreferenceFragment() : new DeactivatedPinPreferenceFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(android.R.id.content, fragment)
                    .commit();
        }
    }
}
