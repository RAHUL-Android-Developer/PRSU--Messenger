package org.thoughtcrime.securesms.pin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.autofill.HintConstants;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.ProfileUploadJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.v2.SvrConstants;
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.edit.CreateProfileActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.registration.RegistrationUtil;
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.SupportEmailUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton;


public class PinRestoreEntryFragment extends LoggingFragment {
  private static final String TAG = Log.tag(PinRestoreActivity.class);

  private static final int MINIMUM_PIN_LENGTH = 6;

  private EditText pinEntry1, pinEntry2, pinEntry3, pinEntry4, pinEntry5, pinEntry6;
  private View                           helpButton;
  private View                           skipButton;
  private CircularProgressMaterialButton pinButton;
  private TextView                       errorLabel,reset;
  private MaterialButton                 keyboardToggle;
  private PinRestoreViewModel            viewModel;
  private int                            selectedPosition = 0;
  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.pin_restore_entry_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    initViews(view);
    initViewModel();
  }

  private void initViews(@NonNull View root) {
    RegistrationViewDelegate.setDebugLogSubmitMultiTapView(root.findViewById(R.id.pin_restore_pin_title));


    pinEntry1 = root.findViewById(R.id.pinEntry1);
    pinEntry2 = root.findViewById(R.id.pinEntry2);
    pinEntry3 = root.findViewById(R.id.pinEntry3);
    pinEntry4 = root.findViewById(R.id.pinEntry4);
    pinEntry5 = root.findViewById(R.id.pinEntry5);
    pinEntry6 = root.findViewById(R.id.pinEntry6);


    pinButton      = root.findViewById(R.id.pin_restore_pin_confirm);
    errorLabel     = root.findViewById(R.id.pin_restore_pin_input_label);
    keyboardToggle = root.findViewById(R.id.pin_restore_keyboard_toggle);
    helpButton     = root.findViewById(R.id.pin_restore_forgot_pin);
    skipButton     = root.findViewById(R.id.pin_restore_skip_button);
    reset     = root.findViewById(R.id.reset);




    pinEntry1.addTextChangedListener(textWatcher);
    pinEntry2.addTextChangedListener(textWatcher);
    pinEntry3.addTextChangedListener(textWatcher);
    pinEntry4.addTextChangedListener(textWatcher);
    pinEntry5.addTextChangedListener(textWatcher);
    pinEntry6.addTextChangedListener(textWatcher);

    showKeyboard(pinEntry1);
    disableEditText(pinEntry2);
    disableEditText(pinEntry3);
    disableEditText(pinEntry4);
    disableEditText(pinEntry5);
    disableEditText(pinEntry6);


    disableAfterFill(pinEntry1);
    disableAfterFill(pinEntry2);
    disableAfterFill(pinEntry3);
    disableAfterFill(pinEntry4);
    disableAfterFill(pinEntry5);
    disableAfterFill(pinEntry6);




    helpButton.setVisibility(View.GONE);
    helpButton.setOnClickListener(v -> onNeedHelpClicked());

    skipButton.setOnClickListener(v -> onSkipClicked());

    pinEntry1.setImeOptions(EditorInfo.IME_ACTION_DONE);
    pinEntry1.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        ViewUtil.hideKeyboard(requireContext(), v);
        onPinSubmitted();
        return true;
      }
      return false;
    });
    ViewCompat.setAutofillHints(pinEntry1, HintConstants.AUTOFILL_HINT_PASSWORD);

    enableAndFocusPinEntry();

    pinButton.setOnClickListener((v) -> {
      ViewUtil.hideKeyboard(requireContext(), pinEntry1);
      onPinSubmitted();
    });

    reset.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        resetPins();
      }
    });


    keyboardToggle.setOnClickListener((v) -> {
      PinKeyboardType keyboardType = getPinEntryKeyboardType();

      keyboardToggle.setIconResource(keyboardType.getIconResource());

      updateKeyboard(keyboardType.getOther());
    });

    keyboardToggle.setIconResource(getPinEntryKeyboardType().getOther().getIconResource());
  }

  private void initViewModel() {
    viewModel = new ViewModelProvider(this).get(PinRestoreViewModel.class);

    viewModel.triesRemaining.observe(getViewLifecycleOwner(), this::presentTriesRemaining);
    viewModel.getEvent().observe(getViewLifecycleOwner(), this::presentEvent);
  }

  private void presentTriesRemaining(PinRestoreViewModel.TriesRemaining triesRemaining) {
    if (triesRemaining.hasIncorrectGuess()) {
      if (triesRemaining.getCount() == 1) {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.PinRestoreEntryFragment_incorrect_pin)
            .setMessage(getResources().getQuantityString(R.plurals.PinRestoreEntryFragment_you_have_d_attempt_remaining, triesRemaining.getCount(), triesRemaining.getCount()))
            .setPositiveButton(android.R.string.ok, null)
            .show();
        resetPins();
      }

      errorLabel.setText(R.string.PinRestoreEntryFragment_incorrect_pin);
      helpButton.setVisibility(View.VISIBLE);
    } else {
      if (triesRemaining.getCount() == 1) {
        helpButton.setVisibility(View.VISIBLE);
        new MaterialAlertDialogBuilder(requireContext())
            .setMessage(getResources().getQuantityString(R.plurals.PinRestoreEntryFragment_you_have_d_attempt_remaining, triesRemaining.getCount(), triesRemaining.getCount()))
            .setPositiveButton(android.R.string.ok, null)
            .show();
        resetPins();

      }
    }

    if (triesRemaining.getCount() == 0) {
      Log.w(TAG, "Account locked. User out of attempts on KBS.");
      onAccountLocked();
    }
  }

  private void presentEvent(@NonNull PinRestoreViewModel.Event event) {
    switch (event) {
      case SUCCESS:
        handleSuccess();
        break;
      case EMPTY_PIN:
        Toast.makeText(requireContext(), R.string.RegistrationActivity_you_must_enter_your_registration_lock_PIN, Toast.LENGTH_LONG).show();
        pinButton.cancelSpinning();
        resetPins();
        enableAndFocusPinEntry();
        break;
      case PIN_TOO_SHORT:
        Toast.makeText(requireContext(), getString(R.string.RegistrationActivity_your_pin_has_at_least_d_digits_or_characters, MINIMUM_PIN_LENGTH), Toast.LENGTH_LONG).show();
        pinButton.cancelSpinning();
        resetPins();
        enableAndFocusPinEntry();
        break;
      case PIN_INCORRECT:
        pinButton.cancelSpinning();
        resetPins();
        enableAndFocusPinEntry();
        break;
      case PIN_LOCKED:
        onAccountLocked();
        break;
      case NETWORK_ERROR:
        Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show();
        pinButton.cancelSpinning();
        pinEntry1.setEnabled(true);
        enableAndFocusPinEntry();
        break;
    }
  }

  private PinKeyboardType getPinEntryKeyboardType() {
    boolean isNumeric = (pinEntry1.getInputType() & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER;

    return isNumeric ? PinKeyboardType.NUMERIC : PinKeyboardType.ALPHA_NUMERIC;
  }

  private void onPinSubmitted() {
    pinEntry1.setEnabled(false);

    String pin = pinEntry1.getText().toString() +
                 pinEntry2.getText().toString() +
                 pinEntry3.getText().toString() +
                 pinEntry4.getText().toString() +
                 pinEntry5.getText().toString() +
                 pinEntry6.getText().toString();
    viewModel.onPinSubmitted(pin, getPinEntryKeyboardType());
    pinButton.setSpinning();
  }

  private void onNeedHelpClicked() {
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.PinRestoreEntryFragment_need_help)
        .setMessage(getString(R.string.PinRestoreEntryFragment_your_pin_is_a_d_digit_code, SvrConstants.MINIMUM_PIN_LENGTH))
        .setPositiveButton(R.string.PinRestoreEntryFragment_create_new_pin, ((dialog, which) -> {
          SvrRepository.onPinRestoreForgottenOrSkipped();
          ((PinRestoreActivity) requireActivity()).navigateToPinCreation();
        }))
        .setNeutralButton(R.string.PinRestoreEntryFragment_contact_support, (dialog, which) -> {
          String body = SupportEmailUtil.generateSupportEmailBody(requireContext(),
                                                                  R.string.PinRestoreEntryFragment_signal_registration_need_help_with_pin,
                                                                  null,
                                                                  null);
          CommunicationActions.openEmail(requireContext(),
                                         SupportEmailUtil.getSupportEmailAddress(requireContext()),
                                         getString(R.string.PinRestoreEntryFragment_signal_registration_need_help_with_pin),
                                         body);
        })
        .setNegativeButton(R.string.PinRestoreEntryFragment_cancel, null)
        .show();
  }

  private void onSkipClicked() {
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.PinRestoreEntryFragment_skip_pin_entry)
        .setMessage(R.string.PinRestoreEntryFragment_if_you_cant_remember_your_pin)
        .setPositiveButton(R.string.PinRestoreEntryFragment_create_new_pin, (dialog, which) -> {
          SvrRepository.onPinRestoreForgottenOrSkipped();
          ((PinRestoreActivity) requireActivity()).navigateToPinCreation();
        })
        .setNegativeButton(R.string.PinRestoreEntryFragment_cancel, null)
        .show();
  }

  private void onAccountLocked() {
    SvrRepository.onPinRestoreForgottenOrSkipped();
    SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), PinRestoreEntryFragmentDirections.actionAccountLocked());
  }

  private void handleSuccess() {
    pinButton.cancelSpinning();
    SignalStore.onboarding().clearAll();

    Activity activity = requireActivity();

    if (Recipient.self().getProfileName().isEmpty() || !AvatarHelper.hasAvatar(activity, Recipient.self().getId())) {
      final Intent main    = MainActivity.clearTop(activity);
      final Intent profile = CreateProfileActivity.getIntentForUserProfile(activity);

      profile.putExtra("next_intent", main);
      startActivity(profile);
    } else {
      RegistrationUtil.maybeMarkRegistrationComplete();
      ApplicationDependencies.getJobManager().add(new ProfileUploadJob());
      startActivity(MainActivity.clearTop(activity));
    }

    activity.finish();
  }

  private void updateKeyboard(@NonNull PinKeyboardType keyboard) {
    boolean isAlphaNumeric = keyboard == PinKeyboardType.ALPHA_NUMERIC;

    pinEntry1.setInputType(isAlphaNumeric ? InputType.TYPE_CLASS_TEXT   | InputType.TYPE_TEXT_VARIATION_PASSWORD
                                          : InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

    pinEntry1.getText().clear();
  }

  private void enableAndFocusPinEntry() {
    pinEntry1.setEnabled(true);
    pinEntry1.setFocusable(true);
    ViewUtil.focusAndShowKeyboard(pinEntry1);
  }


  //rahul

  private void resetPins() {
    pinEntry1.setText("");
    pinEntry2.setText("");
    pinEntry3.setText("");
    pinEntry4.setText("");
    pinEntry5.setText("");
    pinEntry6.setText("");
    enableEditText(pinEntry1);
    disableEditText(pinEntry2);
    disableEditText(pinEntry3);
    disableEditText(pinEntry4);
    disableEditText(pinEntry5);
    disableEditText(pinEntry6);
    selectedPosition = 0;
  }

  private void showKeyboard(EditText input) {
    Log.d("position:", "Position " + selectedPosition);
    input.requestFocus();
    InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    inputMethodManager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);

  }

  private TextWatcher textWatcher = new TextWatcher() {
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {

      if (s.length() > 0) {
        moveFocusToNextInput();

      }
//      String pin = pinEntry1.getText().toString() +
//                   pinEntry2.getText().toString() +
//                   pinEntry3.getText().toString() +
//                   pinEntry4.getText().toString() +
//                   pinEntry5.getText().toString() +
//                   pinEntry6.getText().toString();

    }
  };

  private void moveFocusToNextInput() {
    switch (selectedPosition) {
      case 0:
        selectedPosition = 1;
        enableInput(pinEntry2);
        break;
      case 1:
        selectedPosition = 2;
        enableInput(pinEntry3);
        break;
      case 2:
        selectedPosition = 3;
        enableInput(pinEntry4);
        break;
      case 3:
        selectedPosition = 4;
        enableInput(pinEntry5);
        break;
      case 4:
        selectedPosition = 5;
        enableInput(pinEntry6);
        break;
      case 5:
        break;
    }
  }

  private void enableInput(EditText editText) {
    editText.setEnabled(true);
    editText.setFocusable(true);
    editText.setFocusableInTouchMode(true);
    editText.requestFocus();
    showKeyboard(editText);
  }

  private void disableInput(EditText editText) {
    editText.setEnabled(false);
    editText.setFocusable(false);
    editText.setFocusableInTouchMode(false);
  }

  private void disableAfterFill(EditText editText) {
    editText.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {}

      @Override
      public void afterTextChanged(Editable s) {
        if (s.length() > 0) {
          disableInput(editText);
        }
      }
    });
  }

  private void enableEditText(EditText editText) {
    editText.setEnabled(true);
    editText.setFocusable(true);
    editText.setFocusableInTouchMode(true);
  }

  private void disableEditText(EditText editText) {
    editText.setEnabled(false);
    editText.setFocusable(false);
    editText.setFocusableInTouchMode(false);
  }

}