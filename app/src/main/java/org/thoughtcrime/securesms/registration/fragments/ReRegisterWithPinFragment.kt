package org.thoughtcrime.securesms.registration.fragments


import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.d
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.PinRestoreEntryFragmentBinding
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import org.thoughtcrime.securesms.lock.v2.SvrConstants
import org.thoughtcrime.securesms.registration.VerifyResponseWithRegistrationLockProcessor
import org.thoughtcrime.securesms.registration.viewmodel.ReRegisterWithPinViewModel
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SupportEmailUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate


/**
 * Using a recovery password or restored KBS token attempt to register in the skip flow.
 */
class ReRegisterWithPinFragment : LoggingFragment(R.layout.pin_restore_entry_fragment) {


  private var selectedPosition = 0
  companion object {
    private val TAG = Log.tag(ReRegisterWithPinFragment::class.java)
  }


  private var _binding: PinRestoreEntryFragmentBinding? = null
  private val binding: PinRestoreEntryFragmentBinding
    get() = _binding!!

  private val registrationViewModel: RegistrationViewModel by activityViewModels()
  private val reRegisterViewModel: ReRegisterWithPinViewModel by viewModels()

  private val disposables = LifecycleDisposable()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    _binding = PinRestoreEntryFragmentBinding.bind(view)

    d("Re-gistraction","ashdhfc");

    disposables.bindTo(viewLifecycleOwner.lifecycle)

    RegistrationViewDelegate.setDebugLogSubmitMultiTapView(binding.pinRestorePinTitle)

    binding.pinRestorePinDescription.setText(R.string.RegistrationLockFragment__enter_the_pin_you_created_for_your_account)

    binding.pinRestoreForgotPin.visibility = View.GONE
    binding.pinRestoreForgotPin.setOnClickListener { onNeedHelpClicked() }

    binding.reset.setOnClickListener{
      resetPins()

    }
    binding.pinRestoreSkipButton.setOnClickListener { onSkipClicked() }

    binding.pinEntry1.addTextChangedListener(textWatcher)
    binding.pinEntry2.addTextChangedListener(textWatcher)
    binding.pinEntry3.addTextChangedListener(textWatcher)
    binding.pinEntry4.addTextChangedListener(textWatcher)
    binding.pinEntry5.addTextChangedListener(textWatcher)
    binding.pinEntry6.addTextChangedListener(textWatcher)

    showKeyboard(binding.pinEntry1)
    disableEditText(binding.pinEntry2)
    disableEditText(binding.pinEntry3)
    disableEditText(binding.pinEntry4)
    disableEditText(binding.pinEntry5)
    disableEditText(binding.pinEntry6)


    disableAfterFill(binding.pinEntry1)
    disableAfterFill(binding.pinEntry2)
    disableAfterFill(binding.pinEntry3)
    disableAfterFill(binding.pinEntry4)
    disableAfterFill(binding.pinEntry5)
    disableAfterFill(binding.pinEntry6)






    binding.pinEntry1.imeOptions = EditorInfo.IME_ACTION_DONE
    binding.pinEntry2.imeOptions = EditorInfo.IME_ACTION_DONE
    binding.pinEntry3.imeOptions = EditorInfo.IME_ACTION_DONE
    binding.pinEntry4.imeOptions = EditorInfo.IME_ACTION_DONE
    binding.pinEntry5.imeOptions = EditorInfo.IME_ACTION_DONE
    binding.pinEntry6.imeOptions = EditorInfo.IME_ACTION_DONE
    binding.pinEntry1.setOnEditorActionListener { v, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        ViewUtil.hideKeyboard(requireContext(), v!!)
        handlePinEntry()
        return@setOnEditorActionListener true
      }
      false
    }

    enableAndFocusPinEntry()

    binding.pinRestorePinConfirm.setOnClickListener {
      d("Re-gistraction","ashdhfc");
      handlePinEntry()
    }

    binding.pinRestoreKeyboardToggle.setOnClickListener {
      val currentKeyboardType: PinKeyboardType = getPinEntryKeyboardType()
      updateKeyboard(currentKeyboardType.other)
      binding.pinRestoreKeyboardToggle.setIconResource(currentKeyboardType.iconResource)
    }

    binding.pinRestoreKeyboardToggle.setIconResource(getPinEntryKeyboardType().other.iconResource)

    reRegisterViewModel.updateSvrTriesRemaining(registrationViewModel.svrTriesRemaining)

    disposables += reRegisterViewModel.triesRemaining.subscribe(this::updateTriesRemaining)
  }

  override fun onDestroyView() {
    _binding = null
    super.onDestroyView()
  }

  private fun handlePinEntry() {
    val pin: String? = binding.pinEntry1.text?.toString()+binding.pinEntry2.text?.toString()+binding.pinEntry3.text?.toString()+binding.pinEntry4.text?.toString()+binding.pinEntry5.text?.toString()+binding.pinEntry6.text?.toString()

    val trimmedLength = pin?.trim()?.length ?: 0
    if (trimmedLength == 0) {
      Toast.makeText(requireContext(), R.string.RegistrationActivity_you_must_enter_your_registration_lock_PIN, Toast.LENGTH_LONG).show()
      enableAndFocusPinEntry()
      return
    }

    if (trimmedLength < BaseRegistrationLockFragment.MINIMUM_PIN_LENGTH) {
      Toast.makeText(requireContext(), getString(R.string.RegistrationActivity_your_pin_has_at_least_d_digits_or_characters, BaseRegistrationLockFragment.MINIMUM_PIN_LENGTH), Toast.LENGTH_LONG).show()
      enableAndFocusPinEntry()
      return
    }

    disposables += registrationViewModel.verifyReRegisterWithPin(pin!!)
      .doOnSubscribe {
        ViewUtil.hideKeyboard(requireContext(), binding.pinEntry1)
        ViewUtil.hideKeyboard(requireContext(), binding.pinEntry2)
        ViewUtil.hideKeyboard(requireContext(), binding.pinEntry3)
        ViewUtil.hideKeyboard(requireContext(), binding.pinEntry4)
        ViewUtil.hideKeyboard(requireContext(), binding.pinEntry5)
        ViewUtil.hideKeyboard(requireContext(), binding.pinEntry6)
        binding.pinEntry1.isEnabled = false
        binding.pinEntry2.isEnabled = false
        binding.pinEntry3.isEnabled = false
        binding.pinEntry4.isEnabled = false
        binding.pinEntry5.isEnabled = false
        binding.pinEntry6.isEnabled = false
        binding.pinRestorePinConfirm.setSpinning()
      }
      .doAfterTerminate {
        binding.pinEntry1.isEnabled = true
        binding.pinEntry2.isEnabled = true
        binding.pinEntry3.isEnabled = true
        binding.pinEntry4.isEnabled = true
        binding.pinEntry5.isEnabled = true
        binding.pinEntry6.isEnabled = true
        binding.pinRestorePinConfirm.cancelSpinning()
      }
      .subscribe { processor ->
        if (processor.hasResult()) {
          Log.i(TAG, "Successfully re-registered via skip flow")
          d("TAG", "Local pin matches input, attempting registration call");
          findNavController().safeNavigate(R.id.action_reRegisterWithPinFragment_to_registrationCompletePlaceHolderFragment)
          return@subscribe
        }

        reRegisterViewModel.hasIncorrectGuess = true

        if (processor is VerifyResponseWithRegistrationLockProcessor && processor.wrongPin()) {
          reRegisterViewModel.updateSvrTriesRemaining(processor.svrTriesRemaining)
          if (processor.svrTriesRemaining != null) {
            registrationViewModel.svrTriesRemaining = processor.svrTriesRemaining
          }
          return@subscribe
        } else if (processor.isRegistrationLockPresentAndSvrExhausted()) {
          Log.w(TAG, "Unable to continue skip flow, KBS is locked")
          onAccountLocked()
        } else if (processor.isIncorrectRegistrationRecoveryPassword()) {
          Log.w(TAG, "Registration recovery password was incorrect. Moving to SMS verification.")
          onSkipPinEntry()
        } else if (processor.isServerSentError()) {
          Log.i(TAG, "Error from server, not likely recoverable", processor.error)
          genericErrorDialog()
        } else {
          Log.i(TAG, "Unexpected error occurred", processor.error)
          genericErrorDialog()
        }
      }
  }
  private fun updateTriesRemaining(triesRemaining: Int) {
    if (reRegisterViewModel.hasIncorrectGuess) {
      if (triesRemaining == 1 && !reRegisterViewModel.isLocalVerification) {
        MaterialAlertDialogBuilder(requireContext())
          .setTitle(R.string.PinRestoreEntryFragment_incorrect_pin)
          .setMessage(resources.getQuantityString(R.plurals.PinRestoreEntryFragment_you_have_d_attempt_remaining, triesRemaining, triesRemaining))
          .setPositiveButton(android.R.string.ok, null)
          .show()
        resetPins()
      }

      if (triesRemaining > 5) {
        binding.pinRestorePinInputLabel.setText(R.string.PinRestoreEntryFragment_incorrect_pin)
        resetPins()
      } else {
        binding.pinRestorePinInputLabel.text = resources.getQuantityString(R.plurals.RegistrationLockFragment__incorrect_pin_d_attempts_remaining, triesRemaining, triesRemaining)
        resetPins()
      }
      binding.pinRestoreForgotPin.visibility = View.VISIBLE
    } else {
      if (triesRemaining == 1) {
        binding.pinRestoreForgotPin.visibility = View.VISIBLE
        if (!reRegisterViewModel.isLocalVerification) {
          MaterialAlertDialogBuilder(requireContext())
            .setMessage(resources.getQuantityString(R.plurals.PinRestoreEntryFragment_you_have_d_attempt_remaining, triesRemaining, triesRemaining))
            .setPositiveButton(android.R.string.ok, null)
            .show()
          resetPins()
        }
      }
    }

    if (triesRemaining == 0) {
      Log.w(TAG, "Account locked. User out of attempts on KBS.")
      onAccountLocked()
    }
  }

  private fun onAccountLocked() {
    d(TAG, "Showing Incorrect PIN dialog. Is local verification: ${reRegisterViewModel.isLocalVerification}")
    val message = if (reRegisterViewModel.isLocalVerification) R.string.ReRegisterWithPinFragment_out_of_guesses_local else R.string.PinRestoreLockedFragment_youve_run_out_of_pin_guesses

    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.PinRestoreEntryFragment_incorrect_pin)
      .setMessage(message)
      .setCancelable(false)
      .setPositiveButton(R.string.ReRegisterWithPinFragment_send_sms_code) { _, _ -> onSkipPinEntry() }
      .setNegativeButton(R.string.AccountLockedFragment__learn_more) { _, _ -> CommunicationActions.openBrowserLink(requireContext(), getString(R.string.PinRestoreLockedFragment_learn_more_url)) }
      .show()
  }

  private fun enableAndFocusPinEntry() {
    binding.pinEntry1.isEnabled = true
    binding.pinEntry2.isEnabled = true
    binding.pinEntry3.isEnabled = true
    binding.pinEntry4.isEnabled = true
    binding.pinEntry5.isEnabled = true
    binding.pinEntry6.isEnabled = true


    binding.pinEntry1.isFocusable = true
    binding.pinEntry2.isFocusable = true
    binding.pinEntry3.isFocusable = true
    binding.pinEntry4.isFocusable = true
    binding.pinEntry5.isFocusable = true
    binding.pinEntry6.isFocusable = true


    ViewUtil.focusAndShowKeyboard(binding.pinEntry1)
    ViewUtil.focusAndShowKeyboard(binding.pinEntry2)
    ViewUtil.focusAndShowKeyboard(binding.pinEntry3)
    ViewUtil.focusAndShowKeyboard(binding.pinEntry4)
    ViewUtil.focusAndShowKeyboard(binding.pinEntry5)
    ViewUtil.focusAndShowKeyboard(binding.pinEntry6)
  }

  private fun getPinEntryKeyboardType(): PinKeyboardType {
    val isNumeric = binding.pinEntry1.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_NUMBER
    return if (isNumeric) PinKeyboardType.NUMERIC else PinKeyboardType.ALPHA_NUMERIC
  }

  private fun updateKeyboard(keyboard: PinKeyboardType) {
    val isAlphaNumeric = keyboard == PinKeyboardType.ALPHA_NUMERIC
    binding.pinEntry1.inputType = if (isAlphaNumeric) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    binding.pinEntry1.text?.clear()
  }

  private fun onNeedHelpClicked() {
    val message = if (reRegisterViewModel.isLocalVerification) R.string.ReRegisterWithPinFragment_need_help_local else R.string.PinRestoreEntryFragment_your_pin_is_a_d_digit_code

    val dialog = MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.PinRestoreEntryFragment_need_help)
      .setMessage(getString(message, SvrConstants.MINIMUM_PIN_LENGTH))
      .setPositiveButton(R.string.PinRestoreEntryFragment_skip) { _, _ -> onSkipPinEntry() }
      .setNeutralButton(R.string.PinRestoreEntryFragment_contact_support) { _, _ ->
        val body = SupportEmailUtil.generateSupportEmailBody(requireContext(), R.string.ReRegisterWithPinFragment_support_email_subject, null, null)

        CommunicationActions.openEmail(
          requireContext(),
          SupportEmailUtil.getSupportEmailAddress(requireContext()),
          getString(R.string.ReRegisterWithPinFragment_support_email_subject),
          body
        )
      }
      .setNegativeButton(R.string.PinRestoreEntryFragment_cancel, null)
      .show()

    // Apply custom colors to buttons
    dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(requireContext(), R.color.text1))
    dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(requireContext(), R.color.text1))
    dialog.getButton(DialogInterface.BUTTON_NEUTRAL)?.setTextColor(ContextCompat.getColor(requireContext(), R.color.text1))
  }


  private fun onSkipClicked() {
    val message = if (reRegisterViewModel.isLocalVerification) R.string.ReRegisterWithPinFragment_skip_local else R.string.PinRestoreEntryFragment_if_you_cant_remember_your_pin

    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.PinRestoreEntryFragment_skip_pin_entry)
      .setMessage(message)
      .setPositiveButton(R.string.PinRestoreEntryFragment_skip) { _, _ -> onSkipPinEntry() }
      .setNegativeButton(R.string.PinRestoreEntryFragment_cancel, null)
      .show()
  }

  private fun onSkipPinEntry() {
    d(TAG, "User skipping PIN entry.")
    registrationViewModel.setUserSkippedReRegisterFlow(true)
    findNavController().safeNavigate(R.id.action_reRegisterWithPinFragment_to_enterPhoneNumberFragment)
  }

  private fun genericErrorDialog() {
    MaterialAlertDialogBuilder(requireContext())
      .setMessage(R.string.RegistrationActivity_error_connecting_to_service)
      .setPositiveButton(android.R.string.ok, null)
      .show()
  }


  //rahul
  private fun resetPins() {
    binding.pinEntry1.setText("")
    binding.pinEntry2.setText("")
    binding.pinEntry3.setText("")
    binding.pinEntry4.setText("")
    binding.pinEntry5.setText("")
    binding.pinEntry6.setText("")
    enableEditText(binding.pinEntry1)
    disableEditText(binding.pinEntry2)
    disableEditText(binding.pinEntry3)
    disableEditText(binding.pinEntry4)
    disableEditText(binding.pinEntry5)
    disableEditText(binding.pinEntry6)
    selectedPosition = 0
  }

  private fun showKeyboard(input: EditText) {
    d("position:", "Position $selectedPosition")
    input.requestFocus()
    val inputMethodManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
  }

  private val textWatcher: TextWatcher = object : TextWatcher {
    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: Editable) {
      if (s.length > 0) {
        moveFocusToNextInput()
      }
      //      String pin = pinEntry1.getText().toString() +
//                   pinEntry2.getText().toString() +
//                   pinEntry3.getText().toString() +
//                   pinEntry4.getText().toString() +
//                   pinEntry5.getText().toString() +
//                   pinEntry6.getText().toString();

      val pin: String? = binding.pinEntry1.text?.toString()+binding.pinEntry2.text?.toString()+binding.pinEntry3.text?.toString()+binding.pinEntry4.text?.toString()+binding.pinEntry5.text?.toString()+binding.pinEntry6.text?.toString()
      Log.d("Pinnnnnnn", pin)
    }
  }

  private fun moveFocusToNextInput() {
    when (selectedPosition) {
      0 -> {
        selectedPosition = 1
        enableInput(binding.pinEntry2)
      }

      1 -> {
        selectedPosition = 2
        enableInput(binding.pinEntry3)
      }

      2 -> {
        selectedPosition = 3
        enableInput(binding.pinEntry4)
      }

      3 -> {
        selectedPosition = 4
        enableInput(binding.pinEntry5)
      }

      4 -> {
        selectedPosition = 5
        enableInput(binding.pinEntry6)
      }

      5 -> {}
    }
  }

  private fun enableInput(editText: EditText) {
    editText.isEnabled = true
    editText.isFocusable = true
    editText.isFocusableInTouchMode = true
    editText.requestFocus()
    showKeyboard(editText)
  }

  private fun disableInput(editText: EditText) {
    editText.isEnabled = false
    editText.isFocusable = false
    editText.isFocusableInTouchMode = false
  }

  private fun disableAfterFill(editText: EditText) {
    editText.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
      override fun afterTextChanged(s: Editable) {
        if (s.length > 0) {
          disableInput(editText)
        }
      }
    })
  }

  private fun enableEditText(editText: EditText) {
    editText.isEnabled = true
    editText.isFocusable = true
    editText.isFocusableInTouchMode = true
  }

  private fun disableEditText(editText: EditText) {
    editText.isEnabled = false
    editText.isFocusable = false
    editText.isFocusableInTouchMode = false
  }

}
