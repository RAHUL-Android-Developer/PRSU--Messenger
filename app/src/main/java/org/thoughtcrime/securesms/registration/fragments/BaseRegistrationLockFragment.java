package org.thoughtcrime.securesms.registration.fragments;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType;
import org.thoughtcrime.securesms.registration.viewmodel.BaseRegistrationViewModel;
import org.signal.core.util.concurrent.LifecycleDisposable;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

import static org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView;

/**
 * Base fragment used by registration and change number flow to deal with a registration locked account.
 */
public abstract class BaseRegistrationLockFragment extends LoggingFragment {

  private static final String TAG = Log.tag(BaseRegistrationLockFragment.class);

  /**
   * Applies to both V1 and V2 pins, because some V2 pins may have been migrated from V1.
   */
  public static final int MINIMUM_PIN_LENGTH = 6;

  private EditText pinEntry1,pinEntry2,pinEntry3,pinEntry4,pinEntry5,pinEntry6;
  private   View                           forgotPin;
  protected CircularProgressMaterialButton pinButton;
  private   TextView                       errorLabel,reset;
  private   MaterialButton                 keyboardToggle;
  private   long                           timeRemaining;
  private int                            selectedPosition = 0;

  private BaseRegistrationViewModel viewModel;

  private final LifecycleDisposable disposables = new LifecycleDisposable();

  public BaseRegistrationLockFragment(int contentLayoutId) {
    super(contentLayoutId);
  }

  @Override
  @CallSuper
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    setDebugLogSubmitMultiTapView(view.findViewById(R.id.kbs_lock_pin_title));

    pinEntry1       = view.findViewById(R.id.pinEntry1);
    pinEntry2      = view.findViewById(R.id.pinEntry2);
    pinEntry3       = view.findViewById(R.id.pinEntry3);
    pinEntry4       = view.findViewById(R.id.pinEntry4);
    pinEntry5       = view.findViewById(R.id.pinEntry5);
    pinEntry6       = view.findViewById(R.id.pinEntry6);


    pinButton      = view.findViewById(R.id.kbs_lock_pin_confirm);
    errorLabel     = view.findViewById(R.id.kbs_lock_pin_input_label);
    keyboardToggle = view.findViewById(R.id.kbs_lock_keyboard_toggle);
    forgotPin      = view.findViewById(R.id.kbs_lock_forgot_pin);
    reset      = view.findViewById(R.id.reset);



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
    disableAfterFill(pinEntry4);
    disableAfterFill(pinEntry6);







    RegistrationLockFragmentArgs args = RegistrationLockFragmentArgs.fromBundle(requireArguments());

    timeRemaining        = args.getTimeRemaining();

    forgotPin.setVisibility(View.VISIBLE);
    forgotPin.setOnClickListener(v -> handleForgottenPin(timeRemaining));

    pinEntry1.setImeOptions(EditorInfo.IME_ACTION_DONE);
    pinEntry1.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        ViewUtil.hideKeyboard(requireContext(), v);
        handlePinEntry();
        return true;
      }

      return false;
    });

    enableAndFocusPinEntry();

    pinButton.setOnClickListener((v) -> {
//      ViewUtil.hideKeyboard(requireContext(), pinEntry);
      handlePinEntry();
    });

    keyboardToggle.setOnClickListener((v) -> {
      PinKeyboardType keyboardType = getPinEntryKeyboardType();

      updateKeyboard(keyboardType.getOther());
      keyboardToggle.setIconResource(keyboardType.getIconResource());
    });

    PinKeyboardType keyboardType = getPinEntryKeyboardType().getOther();
    keyboardToggle.setIconResource(keyboardType.getIconResource());

    disposables.bindTo(getViewLifecycleOwner().getLifecycle());
    viewModel = getViewModel();

    viewModel.getLockedTimeRemaining()
             .observe(getViewLifecycleOwner(), t -> timeRemaining = t);

    Integer triesRemaining = viewModel.getSvrTriesRemaining();

    if (triesRemaining != null) {
      if (triesRemaining <= 3) {
        int daysRemaining = getLockoutDays(timeRemaining);

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.RegistrationLockFragment__not_many_tries_left)
            .setMessage(getTriesRemainingDialogMessage(triesRemaining, daysRemaining))
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.PinRestoreEntryFragment_contact_support, (dialog, which) -> sendEmailToSupport())
            .show();
      }

      if (triesRemaining < 5) {
        errorLabel.setText(requireContext().getResources().getQuantityString(R.plurals.RegistrationLockFragment__d_attempts_remaining, triesRemaining, triesRemaining));
      }
    }
  }

  protected abstract BaseRegistrationViewModel getViewModel();

  private String getTriesRemainingDialogMessage(int triesRemaining, int daysRemaining) {
    Resources resources = requireContext().getResources();
    String    tries     = resources.getQuantityString(R.plurals.RegistrationLockFragment__you_have_d_attempts_remaining, triesRemaining, triesRemaining);
    String    days      = resources.getQuantityString(R.plurals.RegistrationLockFragment__if_you_run_out_of_attempts_your_account_will_be_locked_for_d_days, daysRemaining, daysRemaining);

    return tries + " " + days;
  }

  protected PinKeyboardType getPinEntryKeyboardType() {
    boolean isNumeric = (pinEntry1.getInputType() & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER;

    return isNumeric ? PinKeyboardType.NUMERIC : PinKeyboardType.ALPHA_NUMERIC;
  }

  private void handlePinEntry() {
    pinEntry1.setEnabled(false);
    pinEntry2.setEnabled(false);
    pinEntry3.setEnabled(false);
    pinEntry4.setEnabled(false);
    pinEntry5.setEnabled(false);
    pinEntry6.setEnabled(false);

    final String pin = pinEntry1.getText().toString() +
                       pinEntry2.getText().toString() +
                       pinEntry3.getText().toString() +
                       pinEntry4.getText().toString() +
                       pinEntry5.getText().toString() +
                       pinEntry6.getText().toString();
    int trimmedLength = pin.replace(" ", "").length();
    if (trimmedLength == 0) {
      Toast.makeText(requireContext(), R.string.RegistrationActivity_you_must_enter_your_registration_lock_PIN, Toast.LENGTH_LONG).show();
      enableAndFocusPinEntry();
      return;
    }

    if (trimmedLength < MINIMUM_PIN_LENGTH) {
      Toast.makeText(requireContext(), getString(R.string.RegistrationActivity_your_pin_has_at_least_d_digits_or_characters, MINIMUM_PIN_LENGTH), Toast.LENGTH_LONG).show();
      enableAndFocusPinEntry();
      return;
    }

    pinButton.setSpinning();

    Disposable verify = viewModel.verifyCodeAndRegisterAccountWithRegistrationLock(pin)
                                 .observeOn(AndroidSchedulers.mainThread())
                                 .subscribe(processor -> {
                                   if (processor.hasResult()) {
                                     handleSuccessfulPinEntry(pin);
                                   } else if (processor.wrongPin()) {
                                     onIncorrectKbsRegistrationLockPin(Objects.requireNonNull(processor.getSvrTriesRemaining()));
                                   } else if (processor.isRegistrationLockPresentAndSvrExhausted() || processor.registrationLock()) {
                                     onKbsAccountLocked();
                                   } else if (processor.rateLimit()) {
                                     onRateLimited();
                                   } else {
                                     Log.w(TAG, "Unable to verify code with registration lock", processor.getError());
                                     onError();
                                   }
                                 });

    disposables.add(verify);
  }
  //Rahul
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
      String pin = pinEntry1.getText().toString() +
                   pinEntry2.getText().toString() +
                   pinEntry3.getText().toString() +
                   pinEntry4.getText().toString() +
                   pinEntry5.getText().toString() +
                   pinEntry6.getText().toString();

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





  ///Old
  public void onIncorrectKbsRegistrationLockPin(int svrTriesRemaining) {
    pinButton.cancelSpinning();
    pinEntry1.getText().clear();
    pinEntry2.getText().clear();
    pinEntry3.getText().clear();
    pinEntry4.getText().clear();
    pinEntry5.getText().clear();
    pinEntry6.getText().clear();
    enableAndFocusPinEntry();

    viewModel.setSvrTriesRemaining(svrTriesRemaining);

    if (svrTriesRemaining == 0) {
      Log.w(TAG, "Account locked. User out of attempts on KBS.");
      onAccountLocked();
      return;
    }

    if (svrTriesRemaining == 3) {
      int daysRemaining = getLockoutDays(timeRemaining);

      new MaterialAlertDialogBuilder(requireContext())
          .setTitle(R.string.RegistrationLockFragment__incorrect_pin)
          .setMessage(getTriesRemainingDialogMessage(svrTriesRemaining, daysRemaining))
          .setPositiveButton(android.R.string.ok, null)
          .show();
    }

    if (svrTriesRemaining > 5) {
      errorLabel.setText(R.string.RegistrationLockFragment__incorrect_pin_try_again);
    } else {
      errorLabel.setText(requireContext().getResources().getQuantityString(R.plurals.RegistrationLockFragment__incorrect_pin_d_attempts_remaining, svrTriesRemaining, svrTriesRemaining));
      forgotPin.setVisibility(View.VISIBLE);
    }
  }

  public void onRateLimited() {
    pinButton.cancelSpinning();
    enableAndFocusPinEntry();

    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.RegistrationActivity_too_many_attempts)
        .setMessage(R.string.RegistrationActivity_you_have_made_too_many_incorrect_registration_lock_pin_attempts_please_try_again_in_a_day)
        .setPositiveButton(android.R.string.ok, null)
        .show();
  }

  public void onKbsAccountLocked() {
    onAccountLocked();
  }

  public void onError() {
    pinButton.cancelSpinning();
    enableAndFocusPinEntry();

    Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show();
  }

  private void handleForgottenPin(long timeRemainingMs) {
    int lockoutDays = getLockoutDays(timeRemainingMs);
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.RegistrationLockFragment__forgot_your_pin)
        .setMessage(requireContext().getResources().getQuantityString(R.plurals.RegistrationLockFragment__for_your_privacy_and_security_there_is_no_way_to_recover, lockoutDays, lockoutDays))
        .setPositiveButton(android.R.string.ok, null)
        .setNeutralButton(R.string.PinRestoreEntryFragment_contact_support, (dialog, which) -> sendEmailToSupport())
        .show();
  }

  private static int getLockoutDays(long timeRemainingMs) {
    return (int) TimeUnit.MILLISECONDS.toDays(timeRemainingMs) + 1;
  }

  private void onAccountLocked() {
    navigateToAccountLocked();
  }

  protected abstract void navigateToAccountLocked();

  private void updateKeyboard(@NonNull PinKeyboardType keyboard) {
    boolean isAlphaNumeric = keyboard == PinKeyboardType.ALPHA_NUMERIC;

    pinEntry1.setInputType(isAlphaNumeric ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                                          : InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

    pinEntry1.getText().clear();
  }

  private void enableAndFocusPinEntry() {
    pinEntry1.setEnabled(true);
    pinEntry2.setEnabled(true);
    pinEntry3.setEnabled(true);
    pinEntry4.setEnabled(true);
    pinEntry5.setEnabled(true);
    pinEntry6.setEnabled(true);



    pinEntry1.setFocusable(true);
    pinEntry2.setFocusable(true);
    pinEntry3.setFocusable(true);
    pinEntry4.setFocusable(true);
    pinEntry5.setFocusable(true);
    pinEntry6.setFocusable(true);
    ViewUtil.focusAndShowKeyboard(pinEntry1);
    ViewUtil.focusAndShowKeyboard(pinEntry2);
    ViewUtil.focusAndShowKeyboard(pinEntry3);
    ViewUtil.focusAndShowKeyboard(pinEntry4);
    ViewUtil.focusAndShowKeyboard(pinEntry5);
    ViewUtil.focusAndShowKeyboard(pinEntry6);
  }

  protected abstract void handleSuccessfulPinEntry(@NonNull String pin);

  protected abstract void sendEmailToSupport();
}
