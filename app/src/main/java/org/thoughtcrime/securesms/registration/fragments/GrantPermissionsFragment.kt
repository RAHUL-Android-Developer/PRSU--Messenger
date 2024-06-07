package org.thoughtcrime.securesms.registration.fragments

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import org.signal.core.ui.Buttons
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel
import org.thoughtcrime.securesms.util.BackupUtil
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp


/**
 * Fragment displayed during registration which allows a user to read through
 * what permissions are granted to Signal and why, and a means to either skip
 * granting those permissions or continue to grant via system dialogs.
 */
class GrantPermissionsFragment : ComposeFragment() {

  private val args by navArgs<GrantPermissionsFragmentArgs>()
  private val viewModel by activityViewModels<RegistrationViewModel>()
  private val isSearchingForBackup = mutableStateOf(false)

  @Composable
  override fun FragmentContent() {
    val isSearchingForBackup by this.isSearchingForBackup

    GrantPermissionsScreen(
      deviceBuildVersion = Build.VERSION.SDK_INT,
      isSearchingForBackup = isSearchingForBackup,
      isBackupSelectionRequired = BackupUtil.isUserSelectionRequired(LocalContext.current),
      onNextClicked = this::onNextClicked,
      onNotNowClicked = this::onNotNowClicked
    )
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  private fun onNextClicked() {
    when (args.welcomeAction) {
      WelcomeAction.CONTINUE -> {
        WelcomeFragment.continueClicked(
          this,
          viewModel,
          { isSearchingForBackup.value = true },
          { isSearchingForBackup.value = false },
          GrantPermissionsFragmentDirections.actionSkipRestore(),
          GrantPermissionsFragmentDirections.actionRestore()
        )
      }

      WelcomeAction.RESTORE_BACKUP -> {
        WelcomeFragment.restoreFromBackupClicked(
          this,
          viewModel,
          GrantPermissionsFragmentDirections.actionTransferOrRestore()
        )
      }
    }
  }

  private fun onNotNowClicked() {
    when (args.welcomeAction) {
      WelcomeAction.CONTINUE -> {
        WelcomeFragment.gatherInformationAndContinue(
          this,
          viewModel,
          { isSearchingForBackup.value = true },
          { isSearchingForBackup.value = false },
          GrantPermissionsFragmentDirections.actionSkipRestore(),
          GrantPermissionsFragmentDirections.actionRestore()
        )
      }

      WelcomeAction.RESTORE_BACKUP -> {
        WelcomeFragment.gatherInformationAndChooseBackup(
          this,
          viewModel,
          GrantPermissionsFragmentDirections.actionTransferOrRestore()
        )
      }
    }
  }

  /**
   * Which welcome action the user selected which prompted this
   * screen.
   */
  enum class WelcomeAction {
    CONTINUE,
    RESTORE_BACKUP
  }
}

@Preview
@Composable
fun GrantPermissionsScreenPreview() {
  SignalTheme(isDarkMode = false) {
    GrantPermissionsScreen(
      deviceBuildVersion = 33,
      isBackupSelectionRequired = true,
      isSearchingForBackup = true,
      {},
      {}
    )
  }
}

@Composable
fun GrantPermissionsScreen(
  deviceBuildVersion: Int,
  isBackupSelectionRequired: Boolean,
  isSearchingForBackup: Boolean,

  onNextClicked: () -> Unit,
  onNotNowClicked: () -> Unit
) {
  Surface {
    Box(
      modifier = Modifier.fillMaxSize()
    ) {
      Image(
        painter = painterResource(id = R.drawable.bgimg), // Background image
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.FillBounds
      )
      Column(
        modifier = Modifier
          .padding(horizontal = 24.dp)
          .padding(top = 80.dp, bottom = 70.dp)
      ) {
        LazyColumn(
          modifier = Modifier.weight(1f)
        ) {
          item {
            Text(
              text = stringResource(id = R.string.GrantPermissionsFragment__allow_permissions),
              color = colorResource(id = R.color.text1),
              style = MaterialTheme.typography.headlineMedium,
              fontFamily = FontFamily(Font(R.font.opensansbold)),
              fontSize = 20.sp

            )
          }

          item {
            Text(
              text = stringResource(id = R.string.GrantPermissionsFragment__to_help_you_message_people_you_know),
              color = colorResource(id = R.color.text3),
              modifier = Modifier.padding(top = 0.dp, bottom = 41.dp),
              style = TextStyle(
                fontFamily = FontFamily(Font(R.font.opensans_semibold)),
                fontSize = 14.sp
              )

            )
          }

          if (deviceBuildVersion >= 33) {
            item {
              PermissionRow(
                imagePainter = painterResource(id = R.drawable.permission_notificationssss), // Notification image (PNG)
                title = stringResource(id = R.string.GrantPermissionsFragment__notifications),
                subtitle = stringResource(id = R.string.GrantPermissionsFragment__get_notified_when)
              )
            }
          }

          item {
            PermissionRow(
              imagePainter = painterResource(id = R.drawable.user_permission), // Contacts image (PNG)
              title = stringResource(id = R.string.GrantPermissionsFragment__contacts),
              subtitle = stringResource(id = R.string.GrantPermissionsFragment__find_people_you_know)
            )
          }

          if (deviceBuildVersion < 29 || !isBackupSelectionRequired) {
            item {
              PermissionRow(
                imagePainter = painterResource(id = R.drawable.permission_filesss), // Storage image (PNG)
                title = stringResource(id = R.string.GrantPermissionsFragment__storage),
                subtitle = stringResource(id = R.string.GrantPermissionsFragment__send_photos_videos_and_files)
              )
            }
          }

          item {
            PermissionRow(
              imagePainter = painterResource(id = R.drawable.permission_telephone), // Telephone image (PNG)
              title = stringResource(id = R.string.GrantPermissionsFragment__phone_calls),
              subtitle = stringResource(id = R.string.GrantPermissionsFragment__make_registering_easier)
            )
          }
        }

        Row {
          TextButton(onClick = onNotNowClicked) {
            Text(
              color = colorResource(id = R.color.text1),
              text = stringResource(id = R.string.GrantPermissionsFragment__not_now),
              textDecoration = TextDecoration.Underline

            )
          }

          Spacer(modifier = Modifier.weight(1f))

          if (isSearchingForBackup) {
            Box {
              NextButton(
                isSearchingForBackup = true,
                onNextClicked = onNextClicked
              )

              CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
              )
            }
          } else {
            NextButton(
              isSearchingForBackup = false,
              onNextClicked = onNextClicked
            )
          }
        }
      }
    }
  }
}


@Preview
@Composable
fun PermissionRowPreview() {
  PermissionRow(
    imagePainter = painterResource(id = R.drawable.permission_notificationssss), // Notification image (PNG)
    title = stringResource(id = R.string.GrantPermissionsFragment__notifications),
    subtitle = stringResource(id = R.string.GrantPermissionsFragment__get_notified_when)
  )
}

@Composable
fun PermissionRow(
  imagePainter: Painter,
  title: String,
  subtitle: String,
  imageTint: Color = colorResource(id = R.color.text1)
) {
  Surface(
    color = colorResource(id = R.color.text2), // Set the background color using colorResource
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.padding(bottom = 25.dp)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Image(
        painter = imagePainter,
        contentDescription = null,
        modifier = Modifier.size(38.dp),
        colorFilter = if (imageTint != Color.Unspecified) ColorFilter.tint(imageTint) else null // Apply tint color if specified

      )

      Spacer(modifier = Modifier.size(16.dp))

      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          color = Color.Black,
          fontFamily = FontFamily(Font(R.font.opensansbold)),
          fontSize = 16.sp


        )

        Text(
          text = subtitle,
          color = colorResource(id = R.color.text3) ,
          fontSize = 14.sp

        )
      }

      Spacer(modifier = Modifier.size(32.dp))
    }
  }
}


@Composable
fun NextButton(
  isSearchingForBackup: Boolean,
  onNextClicked: () -> Unit
) {
  val alpha = if (isSearchingForBackup) {
    0f
  } else {
    1f
  }

  Button(
    onClick = onNextClicked,
    enabled = !isSearchingForBackup,
    modifier = Modifier
      .alpha(alpha)
      .size(width = 100.dp, height = 45.dp), // Increase the size as needed
    colors = ButtonDefaults.buttonColors(
      containerColor = colorResource(id = R.color.text1),
      contentColor = colorResource(id = R.color.white)
    )
  ) {
    Text(
      text = stringResource(id = R.string.GrantPermissionsFragment__next),
      fontSize = 18.sp,
      fontFamily = FontFamily(Font(R.font.opensansbold)),
      fontWeight = FontWeight.Bold,
      color = Color.White
    )
  }
}

