package org.thoughtcrime.securesms.lock.v2;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.pin.PinOptOutDialog;
import org.thoughtcrime.securesms.registration.RegistrationUtil;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton;
import org.thoughtcrime.securesms.util.views.LearnMoreTextView;

public abstract class BaseSvrPinFragment<ViewModel extends BaseSvrPinViewModel> extends LoggingFragment {

  private TextView title, reset;
  private LearnMoreTextView description;
  EditText input1, input2, input3, input4, input5, input6;
  private TextView                       label;
  private MaterialButton                 keyboardToggle;
  private CircularProgressMaterialButton confirm;
  private ViewModel                      viewModel;
  private int                            selectedPosition = 0;


  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.base_kbs_pin_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    initializeViews(view);

    viewModel = initializeViewModel();
    viewModel.getUserEntry().observe(getViewLifecycleOwner(), svrPin -> {
      boolean isEntryValid = svrPin.length() >= SvrConstants.MINIMUM_PIN_LENGTH;

      confirm.setEnabled(isEntryValid);
      confirm.setAlpha(isEntryValid ? 1f : 0.5f);
    });

    viewModel.getKeyboard().observe(getViewLifecycleOwner(), keyboardType -> {
      updateKeyboard(keyboardType);
      keyboardToggle.setText(resolveKeyboardToggleText(keyboardType));
      keyboardToggle.setIconResource(keyboardType.getOther().getIconResource());
    });

    description.setOnLinkClickListener(v -> {
      CommunicationActions.openBrowserLink(requireContext(), getString(R.string.BaseKbsPinFragment__learn_more_url));
    });

    reset.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        resetPins();
        showKeyboard(input1);

      }
    });

    Toolbar toolbar = view.findViewById(R.id.kbs_pin_toolbar);
    ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);
    ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle(null);

    initializeListeners();
  }

  @Override
  public void onResume() {
    super.onResume();

    input1.requestFocus();
  }

  @Override
  public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
    inflater.inflate(R.menu.pin_skip, menu);
  }

  @Override
  public void onPrepareOptionsMenu(@NonNull Menu menu) {
    if (SignalStore.svr().isRegistrationLockEnabled() ||
        SignalStore.svr().hasPin() ||
        SignalStore.svr().hasOptedOut())
    {
      menu.clear();
    }
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == R.id.menu_pin_learn_more) {
      onLearnMore();
      return true;
    } else if (item.getItemId() == R.id.menu_pin_skip) {
      onPinSkipped();
      return true;
    } else {
      return false;
    }
  }

  protected abstract ViewModel initializeViewModel();

  protected abstract void initializeViewStates();

  protected TextView getTitle() {
    return title;
  }

  protected LearnMoreTextView getDescription() {
    return description;
  }

  protected EditText getInput() {
    return input1;
  }


  protected TextView getLabel() {
    return label;
  }

  protected TextView getKeyboardToggle() {
    return keyboardToggle;
  }

  protected CircularProgressMaterialButton getConfirm() {
    return confirm;
  }

  protected void closeNavGraphBranch() {
    Intent activityIntent = requireActivity().getIntent();
    if (activityIntent != null && activityIntent.hasExtra("next_intent")) {
      startActivity(activityIntent.getParcelableExtra("next_intent"));
    }

    requireActivity().finish();
  }

  private void initializeViews(@NonNull View view) {
    title          = view.findViewById(R.id.edit_kbs_pin_title);
    description    = view.findViewById(R.id.edit_kbs_pin_description);
    input1         = view.findViewById(R.id.input1);
    label          = view.findViewById(R.id.edit_kbs_pin_input_label);
    keyboardToggle = view.findViewById(R.id.edit_kbs_pin_keyboard_toggle);
    confirm        = view.findViewById(R.id.edit_kbs_pin_confirm);


    input1 = view.findViewById(R.id.input1);
    input2 = view.findViewById(R.id.input2);
    input3 = view.findViewById(R.id.input3);
    input4 = view.findViewById(R.id.input4);
    input5 = view.findViewById(R.id.input5);
    input6 = view.findViewById(R.id.input6);
    reset  = view.findViewById(R.id.reset);


    input1.addTextChangedListener(textWatcher);
    input2.addTextChangedListener(textWatcher);
    input3.addTextChangedListener(textWatcher);
    input4.addTextChangedListener(textWatcher);
    input5.addTextChangedListener(textWatcher);
    input6.addTextChangedListener(textWatcher);

    showKeyboard(input1);
    disableEditText(input2);
    disableEditText(input3);
    disableEditText(input4);
    disableEditText(input5);
    disableEditText(input6);


    disableAfterFill(input1);
    disableAfterFill(input2);
    disableAfterFill(input3);
    disableAfterFill(input4);
    disableAfterFill(input5);
    disableAfterFill(input6);


    initializeViewStates();
  }

  private void initializeListeners() {
//    input1.addTextChangedListener(new AfterTextChanged(s -> viewModel.setUserEntry(s.toString())));
    input1.setImeOptions(EditorInfo.IME_ACTION_NEXT);
    input2.setImeOptions(EditorInfo.IME_ACTION_NEXT);
    input3.setImeOptions(EditorInfo.IME_ACTION_NEXT);
    input4.setImeOptions(EditorInfo.IME_ACTION_NEXT);
    input5.setImeOptions(EditorInfo.IME_ACTION_NEXT);
    input6.setImeOptions(EditorInfo.IME_ACTION_NEXT);


    input1.setOnEditorActionListener(this::handleEditorAction);
    input2.setOnEditorActionListener(this::handleEditorAction);
    input3.setOnEditorActionListener(this::handleEditorAction);
    input4.setOnEditorActionListener(this::handleEditorAction);
    input5.setOnEditorActionListener(this::handleEditorAction);
    input6.setOnEditorActionListener(this::handleEditorAction);


    keyboardToggle.setOnClickListener(v -> viewModel.toggleAlphaNumeric());
    confirm.setOnClickListener(v -> viewModel.confirm());
  }

  private boolean handleEditorAction(@NonNull View view, int actionId, @NonNull KeyEvent event) {
    if (actionId == EditorInfo.IME_ACTION_NEXT && confirm.isEnabled()) {
      viewModel.confirm();
    }

    return true;
  }

  private void updateKeyboard(@NonNull PinKeyboardType keyboard) {
    boolean isAlphaNumeric = keyboard == PinKeyboardType.ALPHA_NUMERIC;

    input1.setInputType(isAlphaNumeric ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                                       : InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
  }

  private @StringRes int resolveKeyboardToggleText(@NonNull PinKeyboardType keyboard) {
    if (keyboard == PinKeyboardType.ALPHA_NUMERIC) {
      return R.string.BaseKbsPinFragment__create_numeric_pin;
    } else {
      return R.string.BaseKbsPinFragment__create_alphanumeric_pin;
    }
  }

  private void onLearnMore() {
    CommunicationActions.openBrowserLink(requireContext(), getString(R.string.KbsSplashFragment__learn_more_link));
  }

  private void onPinSkipped() {
    PinOptOutDialog.show(requireContext(), () -> {
      RegistrationUtil.maybeMarkRegistrationComplete();
      closeNavGraphBranch();
    });
  }

  private void resetPins() {
    input1.setText("");
    input2.setText("");
    input3.setText("");
    input4.setText("");
    input5.setText("");
    input6.setText("");
    enableEditText(input1);
    disableEditText(input2);
    disableEditText(input3);
    disableEditText(input4);
    disableEditText(input5);
    disableEditText(input6);
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
      String pin = input1.getText().toString() +
                   input2.getText().toString() +
                   input3.getText().toString() +
                   input4.getText().toString() +
                   input5.getText().toString() +
                   input6.getText().toString();

      viewModel.setUserEntry(pin.toString());
    }
  };

  private void moveFocusToNextInput() {
    switch (selectedPosition) {
      case 0:
        selectedPosition = 1;
        enableInput(input2);
        break;
      case 1:
        selectedPosition = 2;
        enableInput(input3);
        break;
      case 2:
        selectedPosition = 3;
        enableInput(input4);
        break;
      case 3:
        selectedPosition = 4;
        enableInput(input5);
        break;
      case 4:
        selectedPosition = 5;
        enableInput(input6);
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