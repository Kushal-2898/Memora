package app.memora.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.memora.ui.theme.AccentIndigo
import app.memora.ui.theme.BorderLight
import app.memora.ui.theme.CardWhite
import app.memora.ui.theme.MemoraTypography
import app.memora.ui.theme.PrimaryNavy
import app.memora.ui.theme.TextCaption

@Composable
fun EmailAuthDialog(
    onDismissRequest: () -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
) {
    var isSignUpMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isLoading) onDismissRequest() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("email_auth_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = CardWhite,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (isSignUpMode) "Create an Account" else "Sign In with Email",
                    style = MemoraTypography.title.copy(fontSize = 20.sp),
                    color = PrimaryNavy,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isSignUpMode) {
                        "Sync your memories securely in Firestore"
                    } else {
                        "Access your existing memories and settings"
                    },
                    style = MemoraTypography.caption,
                    color = TextCaption,
                    textAlign = TextAlign.Center,
                )

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFDE8E4),
                    ) {
                        Text(
                            text = errorMessage,
                            color = Color(0xFFC0392B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Email field
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    label = { Text("Email address") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Email,
                            contentDescription = null,
                            tint = TextCaption,
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentIndigo,
                        unfocusedBorderColor = BorderLight,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("email_auth_input_email"),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (min 6 characters)") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = TextCaption,
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = TextCaption,
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (email.isNotBlank() && password.length >= 6 && !isLoading) {
                                if (isSignUpMode) onSignUp(email, password) else onSignIn(email, password)
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentIndigo,
                        unfocusedBorderColor = BorderLight,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("email_auth_input_password"),
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Submit Button
                Button(
                    onClick = {
                        if (isSignUpMode) {
                            onSignUp(email, password)
                        } else {
                            onSignIn(email, password)
                        }
                    },
                    enabled = email.isNotBlank() && password.length >= 6 && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("email_auth_submit_button"),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentIndigo,
                        disabledContainerColor = AccentIndigo.copy(alpha = 0.4f),
                    ),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text(
                            text = if (isSignUpMode) "Create Account" else "Sign In",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Toggle Mode (Sign In <-> Create Account)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (isSignUpMode) "Already have an account?" else "New to Memora?",
                        fontSize = 13.sp,
                        color = TextCaption,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSignUpMode) "Sign In" else "Create Account",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentIndigo,
                        modifier = Modifier
                            .clickable {
                                isSignUpMode = !isSignUpMode
                            }
                            .padding(4.dp)
                            .testTag("email_auth_toggle_mode"),
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(
                    onClick = onDismissRequest,
                    enabled = !isLoading,
                    modifier = Modifier.testTag("email_auth_cancel_button"),
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 13.sp,
                        color = TextCaption,
                    )
                }
            }
        }
    }
}
