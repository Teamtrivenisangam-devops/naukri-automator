/**
 * TDD – RED tests written first, then EmailChipInput implemented to make them GREEN.
 *
 * Created by: Adikarthik Gupta C B
 */
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import EmailChipInput from "./EmailChipInput";

describe("EmailChipInput", () => {
  it("(a) typing a valid email and pressing Enter adds a chip", async () => {
  const onChange = vi.fn();

  render(
    <EmailChipInput
      value={[]}
      onChange={onChange}
    />
  );

  await userEvent.type(
    screen.getByTestId("chip-name-input"),
    "Alice"
  );

  await userEvent.type(
    screen.getByTestId("chip-input"),
    "a@x.com{Enter}"
  );

  expect(onChange).toHaveBeenCalledWith([
    {
      name: "Alice",
      email: "a@x.com",
    },
  ]);
});

  it("(b) invalid email shows error and does NOT add a chip", async () => {
    const onChange = vi.fn();
    render(<EmailChipInput value={[]} onChange={onChange} />);
    const input = screen.getByTestId("chip-input");
    await userEvent.type(input, "not-an-email{Enter}");
    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("(c) Backspace on empty input removes the last chip", async () => {
    const onChange = vi.fn();
    render(<EmailChipInput value={["a@x.com", "b@y.com"]} onChange={onChange} />);
    const input = screen.getByTestId("chip-input");
    await userEvent.click(input);
    await userEvent.keyboard("{Backspace}");
    expect(onChange).toHaveBeenCalledWith(["a@x.com"]);
  });

  it("(d) duplicate email is rejected silently (onChange not called)", async () => {
    const onChange = vi.fn();
    render(<EmailChipInput value={["a@x.com"]} onChange={onChange} />);
    const input = screen.getByTestId("chip-input");
    await userEvent.type(input, "a@x.com{Enter}");
    expect(onChange).not.toHaveBeenCalled();
  });
});
